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


import grpc

import kernel_pb2
import kernel_pb2_grpc


def run():
    channel = grpc.insecure_channel('localhost:50053')
    stub = kernel_pb2_grpc.JupyterKernelStub(channel)
    response = stub.execute(kernel_pb2.ExecuteRequest(code="""
library(googleVis)
df=data.frame(country=c("US", "GB", "BR"), 
              val1=c(10,13,14), 
              val2=c(23,12,32))
Bar <- gvisBarChart(df)
print(Bar, tag = 'chart')
    """))
    for r in response:
        print("output:" + r.output)


if __name__ == '__main__':
    run()
