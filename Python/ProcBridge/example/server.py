
# from procbridge import procbridge
import sys
import procbridge

#XXX: delegate must be a class


import time

delegate = procbridge.Delegate()

@delegate.api
def gettime(self, **kw):
    return time.time()


@delegate.api
def echo(self, echo, **kw):
    return echo


@delegate.api
def add(self, elements, conn, **kw):
    # return {'result': sum(x for x in elements)}  #long version
    # for i in elements:
    #     self.server.write_back(conn, {'schas':i})
    return sum(elements)


@delegate.api
def geterror(self, **kw):
    raise Exception("shit happened")


if __name__ == '__main__':

    host = '127.0.0.1'
    port = 8077

    # start socket server
    server = procbridge.ProcBridgeServer(host, port, delegate)
    server.start()
    print('listening...')

    raw_input("press any key to exit...")

    server.stop()
    print('bye!')
