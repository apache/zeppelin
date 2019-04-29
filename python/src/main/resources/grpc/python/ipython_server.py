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
import ipython_pb2
import ipython_pb2_grpc

is_py2 = sys.version[0] == '2'
if is_py2:
    import Queue as queue
else:
    import queue as queue


class IPython(ipython_pb2_grpc.IPythonServicer):

    def __init__(self, server):
        self._status = ipython_pb2.STARTING
        self._server = server
        # issue with execute_interactive and auto completion: https://github.com/jupyter/jupyter_client/issues/429
        # in all case because ipython does not support run and auto completion at the same time: https://github.com/jupyter/notebook/issues/3763
        # For now we will lock to ensure that there is no concurrent bug that can "hang" the kernel
        self._lock = threading.Lock()

    def start(self):
        print("starting...")
        sys.stdout.flush()
        self._km, self._kc = jupyter_client.manager.start_new_kernel(kernel_name='python')
        self._status = ipython_pb2.RUNNING

    def execute(self, request, context):
        print("execute code:\n")
        print(request.code.encode('utf-8'))
        sys.stdout.flush()
        stderr_queue = queue.Queue(maxsize = 10)
        text_queue = queue.Queue(maxsize = 10)
        png_queue = queue.Queue(maxsize = 5)
        jpeg_queue = queue.Queue(maxsize = 5)
        html_queue = queue.Queue(maxsize = 10)

        def _output_hook(msg):
            msg_type = msg['header']['msg_type']
            content = msg['content']
            print("******************* CONTENT ******************")
            print(str(content)[:400])
            if msg_type == 'stream':
                text_queue.put(content['text'])
            elif msg_type in ('display_data', 'execute_result'):
                if 'text/html' in content['data']:
                    html_queue.put(content['data']['text/html'])
                elif 'image/png' in content['data']:
                    png_queue.put(content['data']['image/png'])
                elif 'image/jpeg' in content['data']:
                    jpeg_queue.put(content['data']['image/jpeg'])
                elif 'text/plain' in content['data']:
                    text_queue.put(content['data']['text/plain'])
                elif 'application/javascript' in content['data']:
                    print('add to html queue: ' + str(content)[:100])
                    html_queue.put('<script> ' + content['data']['application/javascript'] + ' </script>\n')
                elif 'application/vnd.holoviews_load.v0+json' in content['data']:
                    html_queue.put('<script> ' + content['data']['application/vnd.holoviews_load.v0+json'] + ' </script>\n')

            elif msg_type == 'error':
                stderr_queue.put('\n'.join(content['traceback']))

        payload_reply = []
        def execute_worker():
            reply = self._kc.execute_interactive(request.code,
                                          output_hook=_output_hook,
                                          timeout=None)
            payload_reply.append(reply)

        t = threading.Thread(name="ConsumerThread", target=execute_worker)
        with self._lock:
            t.start()
            # We want to ensure that the kernel is alive because in case of OOM or other errors
            # Execution might be stuck there:
            # https://github.com/jupyter/jupyter_client/blob/master/jupyter_client/blocking/client.py#L32
            while t.is_alive() and self.isKernelAlive():
                while not text_queue.empty():
                    output = text_queue.get()
                    yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                                      type=ipython_pb2.TEXT,
                                                      output=output)
                while not html_queue.empty():
                    output = html_queue.get()
                    yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                                      type=ipython_pb2.HTML,
                                                      output=output)
                while not stderr_queue.empty():
                    output = stderr_queue.get()
                    yield ipython_pb2.ExecuteResponse(status=ipython_pb2.ERROR,
                                                      type=ipython_pb2.TEXT,
                                                      output=output)
                while not png_queue.empty():
                    output = png_queue.get()
                    yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                                      type=ipython_pb2.PNG,
                                                      output=output)
                while not jpeg_queue.empty():
                    output = jpeg_queue.get()
                    yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                                      type=ipython_pb2.JPEG,
                                                      output=output)

        # if kernel is not alive (should be same as thread is still alive), means that we face
        # an unexpected issue.
        if not self.isKernelAlive() or t.is_alive():
            yield ipython_pb2.ExecuteResponse(status=ipython_pb2.ERROR,
                                                type=ipython_pb2.TEXT,
                                                output="Ipython kernel has been stopped. Please check logs. It might be because of an out of memory issue.")
            return

        while not text_queue.empty():
            output = text_queue.get()
            yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                              type=ipython_pb2.TEXT,
                                              output=output)
        while not html_queue.empty():
            output = html_queue.get()
            yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                              type=ipython_pb2.HTML,
                                              output=output)
        while not stderr_queue.empty():
            output = stderr_queue.get()
            yield ipython_pb2.ExecuteResponse(status=ipython_pb2.ERROR,
                                              type=ipython_pb2.TEXT,
                                              output=output)
        while not png_queue.empty():
            output = png_queue.get()
            yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                              type=ipython_pb2.PNG,
                                              output=output)
        while not jpeg_queue.empty():
            output = jpeg_queue.get()
            yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                              type=ipython_pb2.JPEG,
                                              output=output)
        if payload_reply:
            result = []
            for payload in payload_reply[0]['content']['payload']:
                if payload['data']['text/plain']:
                    result.append(payload['data']['text/plain'])
            if result:
                yield ipython_pb2.ExecuteResponse(status=ipython_pb2.SUCCESS,
                                                  type=ipython_pb2.TEXT,
                                                  output='\n'.join(result))

    def cancel(self, request, context):
        self._km.interrupt_kernel()
        return ipython_pb2.CancelResponse()

    def complete(self, request, context):
        with self._lock:
            reply = self._kc.complete(request.code, request.cursor, reply=True, timeout=None)
        return ipython_pb2.CompletionResponse(matches=reply['content']['matches'])

    def status(self, request, context):
        return ipython_pb2.StatusResponse(status = self._status)

    def isKernelAlive(self):
        return self._km.is_alive()

    def terminate(self):
        self._km.shutdown_kernel()

    def stop(self, request, context):
        self.terminate()
        return ipython_pb2.StopResponse()


def serve(port):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    ipython = IPython(server)
    ipython_pb2_grpc.add_IPythonServicer_to_server(ipython, server)
    server.add_insecure_port('[::]:' + port)
    server.start()
    ipython.start()
    try:
        while ipython.isKernelAlive():
            time.sleep(5)
    except KeyboardInterrupt:
        print("interrupted")
    finally:
        print("shutdown")
        # we let 2 sc for all request to be complete
        server.stop(2)
        ipython.terminate()
        os._exit(0)

if __name__ == '__main__':
    serve(sys.argv[1])
