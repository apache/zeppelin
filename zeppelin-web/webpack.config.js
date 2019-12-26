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

'use strict';

var path = require('path');
var webpack = require('webpack');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var MiniCssExtractPlugin = require('mini-css-extract-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var InsertLiveReloadPlugin = function InsertLiveReloadPlugin(options) {
  this.options = options || {};
  this.port = this.options.port || 35729;
  this.hostname = this.options.hostname || 'localhost';
}
var express = require('express');
var stringReplacePlugin = require('string-replace-webpack-plugin');

InsertLiveReloadPlugin.prototype.autoloadJs = function autoloadJs() {
  return
};

InsertLiveReloadPlugin.prototype.scriptTag = function scriptTag(source) {
  var reloadScriptTag = [
    '// webpack-livereload-plugin',
    '(function() {',
    '  if (typeof window === "undefined") { return };',
    '  var id = "webpack-livereload-plugin-script";',
    '  if (document.getElementById(id)) { return; }',
    '  var el = document.createElement("script");',
    '  el.id = id;',
    '  el.async = true;',
    '  el.src = "http://' + this.hostname + ':' + this.port + '/livereload.js";',
    '  document.getElementsByTagName("head")[0].appendChild(el);',
    '}());',
    ''
  ].join('\n');
  return reloadScriptTag + source;
};

InsertLiveReloadPlugin.prototype.applyCompilation = function applyCompilation(compilation) {
  compilation.mainTemplate.plugin('startup', this.scriptTag.bind(this));
};

InsertLiveReloadPlugin.prototype.apply = function apply(compiler) {
  this.compiler = compiler;
  compiler.plugin('compilation', this.applyCompilation.bind(this));
};

/**
 * Env
 * Get npm lifecycle event to identify the environment
 */
var ENV = process.env.npm_lifecycle_event;
var isTest = ENV === 'test';
var isProd = ENV.startsWith('build')
var isCI = ENV === 'build:ci'

module.exports = function makeWebpackConfig () {
  /**
   * Config
   * Reference: http://webpack.github.io/docs/configuration.html
   * This is the object where all configuration gets set
   */
  var config = {};

  /**
   * Entry
   * Reference: http://webpack.github.io/docs/configuration.html#entry
   * Should be an empty object if it's generating a test build
   * Karma will set this when it's a test build
   */
  config.entry = isTest ? {} : {
    app: './src/index.js'
  };

  var serverPort = process.env.SERVER_PORT || 8080;
  var webPort = process.env.WEB_PORT || 9000;

  /**
   * Output
   * Reference: http://webpack.github.io/docs/configuration.html#output
   * Should be an empty object if it's generating a test build
   * Karma will handle setting it up for you when it's a test build
   */
  config.output = isTest ? {} : {
    // Absolute output directory
    path: __dirname + '/dist',

    // Output path from the view of the page
    // Uses webpack-dev-server in development
    publicPath: isProd ? '' : 'http://localhost:' + webPort + '/',

    // Filename for entry points
    // Only adds hash in build mode
    filename: isProd ? '[name].[hash].js' : '[name].bundle.js',

    // Filename for non-entry points
    // Only adds hash in build mode
    chunkFilename: isProd ? '[name].[hash].js' : '[name].bundle.js'
  };

  /**
   * Devtool
   * Reference: http://webpack.github.io/docs/configuration.html#devtool
   * Type of sourcemap to use per build type
   */
  config.devtool = 'eval-source-map';
  if (isTest) {
    config.devtool = 'inline-source-map';
  } else if (isProd) {
    config.devtool = 'source-map';
  } else {
    config.devtool = 'eval-source-map';
  }

  /**
   * Loaders
   * Reference: http://webpack.github.io/docs/configuration.html#module-loaders
   * List: http://webpack.github.io/docs/list-of-loaders.html
   * This handles most of the magic responsible for converting modules
   */

  // Initialize module
  config.module = {
    rules: [{
      // headroom 0.9.3 doesn't work with webpack
      // https://github.com/WickyNilliams/headroom.js/issues/213#issuecomment-281106943
      test: require.resolve('headroom.js'),
      use: 'imports-loader?this=>window,define=>false,exports=>false'
    }, {
      // JS LOADER
      // Reference: https://github.com/babel/babel-loader
      // Transpile .js files using babel-loader
      // Compiles ES6 and ES7 into ES5 code
      test: /\.(js|jsx)$/,
      use: ['ng-annotate-loader', 'babel-loader'],
      exclude: /(node_modules|bower_components)/,
    }, {
      // CSS LOADER
      // Reference: https://github.com/webpack/css-loader
      // Allow loading css through js
      //
      // Reference: https://github.com/postcss/postcss-loader
      // Postprocess your css with PostCSS plugins
      test: /\.(sa|sc|c)ss$/,
      // Reference: https://github.com/webpack-contrib/mini-css-extract-plugin
      // Extract css files in production builds
      //
      // Use style-loader in development.
      use: [
        !isProd ? 'style-loader' : MiniCssExtractPlugin.loader,
        'css-loader',
        {
          loader: 'postcss-loader',
          options: {
            ident: 'postcss',
            plugins: [
              require('autoprefixer')()
            ]
          }
        }]
    }, {
      // ASSET LOADER
      // Reference: https://github.com/webpack/file-loader
      // Copy png, jpg, jpeg, gif, svg, woff, woff2, ttf, eot files to output
      // Rename the file using the asset hash
      // Pass along the updated reference to your code
      // You can add here any file extension you want to get copied to your output
      test: /\.(png|jpg|jpeg|gif|svg|woff|woff2|ttf|eot)$/,
      use: 'file-loader'
    }, {
      // HTML LOADER
      // Reference: https://github.com/webpack/raw-loader
      // Allow loading html through js
      test: /\.html$/,
      use: 'raw-loader'
    }, {
      // STRING REPLACE PLUGIN
      // reference: https://www.npmjs.com/package/string-replace-webpack-plugin
      // Allow for arbitrary strings to be replaced as part of the module build process
      // Configure replacements for file patterns
      test: /index.html$/,
      use: stringReplacePlugin.replace({
        replacements: [{
          pattern: /WEB_PORT/ig,
          replacement: function (match, p1, offset, string) {
            return webPort;
          }
        }
      ]})
    }],
  };

  /**
   * Plugins
   * Reference: http://webpack.github.io/docs/configuration.html#plugins
   * List: http://webpack.github.io/docs/list-of-plugins.html
   */
  config.plugins = [
      // Reference: https://github.com/webpack-contrib/mini-css-extract-plugin
      new MiniCssExtractPlugin({
        filename: !isProd ? '[name].css' : '[name].[hash].css',
        chunkFilename: !isProd ? '[id].css' : '[id].[hash].css'
      })
  ];

  // Skip rendering index.html in test mode
  if (!isTest) {
    // Reference: https://github.com/ampedandwired/html-webpack-plugin
    // Render index.html
    config.plugins.push(
      new HtmlWebpackPlugin({
        template: './src/index.html',
        inject: 'body'
      }),
      // Reference: https://webpack.github.io/docs/list-of-plugins.html#defineplugin
      new webpack.DefinePlugin({
        'process.env': {
          HELIUM_BUNDLE_DEV: process.env.HELIUM_BUNDLE_DEV,
          SERVER_PORT: serverPort,
          WEB_PORT: webPort,
          PROD: isProd,
          BUILD_CI: (isCI) ? JSON.stringify(true) : JSON.stringify(false)
        }
      })
    )
  }

  if (isTest) {
    config.module.rules = [
      {
        // COVERAGE
        test: /\.js$/,
        exclude: /(node_modules|bower_components|\.test\.js)/,
        loader: 'istanbul-instrumenter',
        enforce: 'post'
      }
    ]
  }

  // Add build specific plugins
  if (isProd) {
    config.optimization = {
      noEmitOnErrors: true,
      minimize: true
    }
    config.plugins.push(
      // Copy assets from the public folder
      // Reference: https://github.com/kevlened/copy-webpack-plugin
      new CopyWebpackPlugin([])
    )
  } else {
    config.plugins.push(
      new InsertLiveReloadPlugin(),
      // reference: https://www.npmjs.com/package/string-replace-webpack-plugin
      new stringReplacePlugin()
    )
  }

  /**
   * Dev server configuration
   * Reference: http://webpack.github.io/docs/configuration.html#devserver
   * Reference: http://webpack.github.io/docs/webpack-dev-server.html
   */
  config.devServer = {
    historyApiFallback: true,
    port: webPort,
    inline: true,
    hot: true,
    progress: true,
    contentBase: './src',
    before: function(app) {
      app.use('**/bower_components/', express.static(path.resolve(__dirname, './bower_components/')));
      app.use('**/app/', express.static(path.resolve(__dirname, './src/app/')));
      app.use('**/assets/', express.static(path.resolve(__dirname, './src/assets/')));
      app.use('**/fonts/', express.static(path.resolve(__dirname, './src/fonts/')));
      app.use('**/components/', express.static(path.resolve(__dirname, './src/components/')));
    },
    stats: 'minimal',
  };

  return config;
}();
