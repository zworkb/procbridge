
# from procbridge import procbridge
import sys
import procbridge

#XXX: delegate must be a class

delegate = procbridge.Delegate()


@delegate.api
def echo(self, echo, **kw):
    return echo


@delegate.api
def add(self, elements, conn, **kw):
    # return {'result': sum(x for x in elements)}  #long version
    for i in range(10):
        self.server.write_back(conn, {'schas':i})
    return sum(elements)


if __name__ == '__main__':

    host = '127.0.0.1'
    port = 8077

    # start socket server
    server = procbridge.ProcBridgeServer(host, port, delegate)
    server.start()
    print('listening...')

    try:
        for line in sys.stdin:
            if line.strip() == 'exit':
                break
    except KeyboardInterrupt:
        pass

    server.stop()
    print('bye!')
