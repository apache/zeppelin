/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

module.exports = {
    entry: './src/load.js',
    output: { path: './', filename: 'helium.bundle.js', },
  module: {
    loaders: [
      {
        test: /\.js$/,
        loader: 'babel-loader',
        exclude: /node_modules\/(?!(zeppelin-spell|zeppelin-vis|zeppelin-tabledata)\/).*/,
        query: {
          presets: [
            [
              "env",
              {
                "targets": {
                  "browsers": ["last 5 version", "> 5%", "edge >= 12", "ie >= 9", "safari 7", "chrome 47", "firefox 31"]
                }
              }
            ]
          ]
        },
      },
      { test: /(\.css)$/, loaders: ['style', 'css?sourceMap&importLoaders=1'], },
      { test: /\.woff(\?\S*)?$/, loader: 'url-loader?limit=10000&minetype=application/font-woff', },
      { test: /\.woff2(\?\S*)?$/, loader: 'url-loader?limit=10000&minetype=application/font-woff', },
      { test: /\.eot(\?\S*)?$/, loader: 'url-loader', }, {
        test: /\.ttf(\?\S*)?$/, loader: 'url-loader', }, {
        test: /\.svg(\?\S*)?$/, loader: 'url-loader', }, {
        test: /\.png(\?\S*)?$/, loader: 'url-loader', }, {
        test: /\.jpg(\?\S*)?$/, loader: 'url-loader', }, {
        test: /\.json$/, loader: 'json-loader' }, ],
  }
}
