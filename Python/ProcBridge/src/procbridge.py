import socket
import json
import threading

_STATUS_CODE_REQUEST = 0
_STATUS_CODE_GOOD_RESPONSE = 1
_STATUS_CODE_BAD_RESPONSE = 2

_KEY_API = 'api'
_KEY_BODY = 'body'
_KEY_MSG = 'msg'

_ERROR_MSG_MALFORMED_DATA = 'malformed data'
_ERROR_MSG_INCOMPATIBLE_VERSION = 'incompatible version'
_ERROR_MSG_INVALID_STATUS_CODE = 'invalid status code'

def bytes2long(buf):
    res = ord(buf[0]) +\
          (ord(buf[1]) << 8) +\
          (ord(buf[2]) << 16) +\
          (ord(buf[3]) << 24)
    return res

def long2bytes(x):
    bytes=''.join(map(chr,
              [
                  x & 255,
                  (x >> 8) & 255,
                  (x >> 16) & 255,
                  (x >> 24) & 255
              ]))
    return bytes

def _read_bytes(s, count) :
    rst = b''
    while True:
        tmp = s.recv(count - len(rst))
        if len(tmp) == 0:
            break
        rst += tmp
        if len(rst) == count:
            break
    return rst


def _read_socket(s):
    # 1. FLAG 'pb'
    flag = _read_bytes(s, 2)
    if flag != b'pb':
        raise Exception(_ERROR_MSG_MALFORMED_DATA)

    # 2. VERSION
    ver = _read_bytes(s, 2)
    if ver != b'\x01\x00':
        raise Exception(_ERROR_MSG_INCOMPATIBLE_VERSION)

    # 3. STATUS CODE
    status_code = _read_bytes(s, 1)
    if len(status_code) != 1:
        raise Exception(_ERROR_MSG_MALFORMED_DATA)
    code = ord(status_code[0])

    # 4. RESERVED (2 bytes)
    reserved = _read_bytes(s, 2)
    if len(reserved) != 2:
        raise Exception(_ERROR_MSG_MALFORMED_DATA)

    # 5. LENGTH (4-byte, little endian)
    len_bytes = _read_bytes(s, 4)
    if len(len_bytes) != 4:
        raise Exception(_ERROR_MSG_MALFORMED_DATA)

    json_len = bytes2long(len_bytes)

    # 6. JSON OBJECT
    text_bytes = _read_bytes(s, json_len)
    if len(text_bytes) != json_len:
        raise Exception(_ERROR_MSG_MALFORMED_DATA)
    obj = json.loads(text_bytes, encoding='utf-8')

    return code, obj


def _write_socket(s, status_code, json_obj):
    # 1. FLAG
    s.sendall(b'pb')
    # 2. VERSION
    s.sendall(b'\x01\x00')
    # 3. STATUS CODE
    s.sendall(bytes([status_code]))
    # 4. RESERVED 2 BYTES
    s.sendall(b'\x00\x00')

    # 5. LENGTH (little endian)
    json_text = json.dumps(json_obj)
    json_bytes = json_text #bytes(json_text, encoding='utf-8')
    # len_bytes = len(json_bytes).to_bytes(4, byteorder='little')
    len_bytes = long2bytes(len(json_bytes))
    s.sendall(len_bytes)

    # 6. JSON
    s.sendall(json_bytes)


def _read_request(s) :
    status_code, obj = _read_socket(s)
    if status_code != _STATUS_CODE_REQUEST:
        raise Exception(_ERROR_MSG_INVALID_STATUS_CODE)
    if _KEY_API not in obj:
        raise Exception(_ERROR_MSG_MALFORMED_DATA)
    if _KEY_BODY in obj:
        return str(obj[_KEY_API]), obj[_KEY_BODY]
    else:
        return str(obj[_KEY_API]), {}


def _read_response(s) :
    status_code, obj = _read_socket(s)
    if status_code == _STATUS_CODE_GOOD_RESPONSE:
        if _KEY_BODY not in obj:
            return status_code, {}
        else:
            return status_code, obj[_KEY_BODY]
    elif status_code == _STATUS_CODE_BAD_RESPONSE:
        if _KEY_MSG not in obj:
            return status_code, ''
        else:
            return status_code, str(obj[_KEY_MSG])
    else:
        raise Exception(_ERROR_MSG_INVALID_STATUS_CODE)


def _write_request(s, api, body):
    _write_socket(s, _STATUS_CODE_REQUEST, {
        _KEY_API: api,
        _KEY_BODY: body
    })


def _write_good_response(s, json_obj):
    _write_socket(s, _STATUS_CODE_GOOD_RESPONSE, {
        _KEY_BODY: json_obj
    })


def _write_bad_response(s, message):
    _write_socket(s, _STATUS_CODE_BAD_RESPONSE, {
        _KEY_MSG: message
    })


class ProcBridge:

    def __init__(self, host, port):
        self.host = host
        self.port = port

    def request(self, api, body=None) :
        if body is None:
            body = {}
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((self.host, self.port))
        try:
            _write_request(s, api, body)
            resp_code, obj = _read_response(s)
            if resp_code == _STATUS_CODE_GOOD_RESPONSE:
                return obj
            else:
                raise Exception(obj)
        finally:
            s.close()


class ProcBridgeServer:

    def __init__(self, host, port, delegate):
        self.host = host
        self.port = port
        self.started = False
        self.lock = threading.Lock()
        self.socket = None
        self.delegate = delegate

    def start(self):
        self.lock.acquire()
        try:
            if self.started:
                return

            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.socket.bind((self.host, self.port))
            self.socket.listen(0)
            t = threading.Thread(target=_start_server_listener, args=(self,))
            t.start()
            self.started = True
        finally:
            self.lock.release()

    def stop(self):
        self.lock.acquire()
        try:
            if not self.started:
                return
            self.socket.close()
            self.socket = None
            self.started = False
        finally:
            self.lock.release()


def _start_server_listener(server):
    try:
        while True:
            server.lock.acquire()
            if not server.started:
                return
            server.lock.release()

            # assert started == true:
            conn, _ = server.socket.accept()
            t = threading.Thread(target=_start_connection, args=(server, conn,))
            t.start()
    # except ConnectionAbortedError:
    except IOError:
        # socket stopped
        pass


def _start_connection(server, s):
    try:
        api, body = _read_request(s)
        try:
            reply = server.delegate(api, body)
            if reply is None:
                reply = {}
            _write_good_response(s, reply)
        except Exception as ex:
            _write_bad_response(s, str(ex))
    except: #TODO: fix that seriously
        raise
        pass
    finally:
        s.close()
