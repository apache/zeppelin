# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from __future__ import print_function

import jupyter_client
import os
import sys
import threading
import time
from concurrent import futures

import grpc
import kernel_pb2
import kernel_pb2_grpc

is_py2 = sys.version[0] == '2'
if is_py2:
    import Queue as queue
else:
    import queue as queue


class KernelServer(kernel_pb2_grpc.JupyterKernelServicer):

    def __init__(self, server, kernel_name):
        self._status = kernel_pb2.STARTING
        self._server = server
        self._kernel_name = kernel_name
        # issue with execute_interactive and auto completion: https://github.com/jupyter/jupyter_client/issues/429
        # in all case because ipython does not support run and auto completion at the same time: https://github.com/jupyter/notebook/issues/3763
        # For now we will lock to ensure that there is no concurrent bug that can "hang" the kernel
        self._lock = threading.Lock()

    def start(self):
        print("starting...")
        sys.stdout.flush()
        self._km, self._kc = jupyter_client.manager.start_new_kernel(kernel_name=self._kernel_name)
        self._status = kernel_pb2.RUNNING

    def execute(self, request, context):
        # print("execute code:\n")
        # print(request.code.encode('utf-8'))
        sys.stdout.flush()
        stream_reply_queue = queue.Queue(maxsize = 30)
        payload_reply = []
        def _output_hook(msg):
            msg_type = msg['header']['msg_type']
            content = msg['content']
            outStatus, outType, output = kernel_pb2.SUCCESS, None, None
            # prepare the reply
            if msg_type == 'stream':
                outType = kernel_pb2.TEXT
                output = content['text']
            elif msg_type in ('display_data', 'execute_result'):
                # print(content['data'])
                # The if-else order matters, can not be changed. Because ipython may provide multiple output.
                # TEXT is the last resort type.
                if 'text/html' in content['data']:
                    outType = kernel_pb2.HTML
                    output = content['data']['text/html']
                elif 'image/jpeg' in content['data']:
                    outType = kernel_pb2.JPEG
                    output = content['data']['image/jpeg']
                elif 'image/png' in content['data']:
                    outType = kernel_pb2.PNG
                    output = content['data']['image/png']
                elif 'application/javascript' in content['data']:
                    outType = kernel_pb2.HTML
                    output = '<script> ' + content['data']['application/javascript'] + ' </script>\n'
                elif 'application/vnd.holoviews_load.v0+json' in content['data']:
                    outType = kernel_pb2.HTML
                    output = '<script> ' + content['data']['application/vnd.holoviews_load.v0+json'] + ' </script>\n'
                elif 'text/plain' in content['data']:
                    outType = kernel_pb2.TEXT
                    output = content['data']['text/plain']
            elif msg_type == 'error':
                outStatus = kernel_pb2.ERROR
                outType = kernel_pb2.TEXT
                output = '\n'.join(content['traceback'])

            # send reply if we supported the output type
            if outType is not None:
                stream_reply_queue.put(
                    kernel_pb2.ExecuteResponse(status=outStatus,
                                                type=outType,
                                                output=output))
        def execute_worker():
            reply = self._kc.execute_interactive(request.code,
                                          output_hook=_output_hook,
                                          timeout=None)
            payload_reply.append(reply)

        t = threading.Thread(name="ConsumerThread", target=execute_worker)
        with self._lock:
            t.start()
            # We want to wait the end of the execution (and queue empty).
            # In our case when the thread is not alive -> it means that the execution is complete
            # However we also ensure that the kernel is alive because in case of OOM or other errors
            # Execution might be stuck there: (might open issue on jupyter client)
            # https://github.com/jupyter/jupyter_client/blob/master/jupyter_client/blocking/client.py#L323
            while (t.is_alive() and self.isKernelAlive()) or not stream_reply_queue.empty():
                # Sleeping time to time to reduce cpu usage.
                # At worst it will bring a 0.05 delay for bunch of messages.
                # Overall it will improve performance.
                time.sleep(0.05)
                while not stream_reply_queue.empty():
                    yield stream_reply_queue.get()

            # if kernel is not alive or thread is still alive, it means that we face an issue.
            if not self.isKernelAlive() or t.is_alive():
                yield kernel_pb2.ExecuteResponse(status=kernel_pb2.ERROR,
                                                  type=kernel_pb2.TEXT,
                                                  output="Ipython kernel has been stopped. Please check logs. It might be because of an out of memory issue.")
        if payload_reply:
            result = []
            for payload in payload_reply[0]['content']['payload']:
                if payload['data']['text/plain']:
                    result.append(payload['data']['text/plain'])
            if result:
                yield kernel_pb2.ExecuteResponse(status=kernel_pb2.SUCCESS,
                                                  type=kernel_pb2.TEXT,
                                                  output='\n'.join(result))

    def cancel(self, request, context):
        self._km.interrupt_kernel()
        return kernel_pb2.CancelResponse()

    def complete(self, request, context):
        with self._lock:
            reply = self._kc.complete(request.code, request.cursor, reply=True, timeout=None)
        return kernel_pb2.CompletionResponse(matches=reply['content']['matches'])

    def status(self, request, context):
        return kernel_pb2.StatusResponse(status = self._status)

    def isKernelAlive(self):
        return self._km.is_alive()

    def terminate(self):
        self._km.shutdown_kernel()

    def stop(self, request, context):
        self.terminate()
        return kernel_pb2.StopResponse()


def serve(kernel_name, port):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    kernel = KernelServer(server, kernel_name)
    kernel_pb2_grpc.add_JupyterKernelServicer_to_server(kernel, server)
    server.add_insecure_port('[::]:' + port)
    server.start()
    kernel.start()
    try:
        while kernel.isKernelAlive():
            time.sleep(5)
    except KeyboardInterrupt:
        print("interrupted")
    finally:
        print("shutdown")
        # we let 2 sc for all request to be complete
        server.stop(2)
        kernel.terminate()
        os._exit(0)

if __name__ == '__main__':
    serve(sys.argv[1], sys.argv[2])
