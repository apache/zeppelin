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

// Generated on 2014-08-29 using generator-angular 0.9.5
'use strict';

// # Globbing
// for performance reasons we're only matching one level down:
// 'test/spec/{,*/}*.js'
// use this if you want to recursively match all subfolders:
// 'test/spec/**/*.js'

module.exports = function(grunt) {

  // Load grunt tasks automatically
  require('load-grunt-tasks')(grunt);

  // Time how long tasks take. Can help when optimizing build times
  require('time-grunt')(grunt);

  // Configurable paths for the application
  var appConfig = {
    app: require('./bower.json').appPath || 'src',
    dist: 'dist'
  };

  // Define the configuration for all the tasks
  grunt.initConfig({

    // Project settings
    yeoman: appConfig,

    babel: {
      options: {
        sourceMap: true,
        presets: ['es2015'],
        plugins: ['transform-object-rest-spread']
      },
      dev: {
        files: [{
          expand: true,
          cwd: './src/',
          src: ['**/*.js'],
          dest: '.tmp',
        }]
      },
      dist: {
        files: [{
          expand: true,
          cwd: '.tmp/concat/scripts',
          src: ['scripts.js'],
          dest: '.tmp/concat/scripts',
        }]
      }
    },

    // use ngAnnotate instead og ngMin
    ngAnnotate: {
      dist: {
        files: [{
          expand: true,
          cwd: '.tmp/concat/scripts',
          src: '*.js',
          dest: '.tmp/concat/scripts'
        }]
      }
    },

    htmlhint: {
      options: {
        'tagname-lowercase': true,
        'attr-lowercase': true,
        'space-tab-mixed-disabled': 'space',
        'tag-pair': true,
        'tag-self-close': true,
        'attr-no-duplication': true
      },
      src: ['src/**/*.html']
    },

    cacheBust: {
      taskName: {
        options: {
          baseDir: '<%= yeoman.dist %>',
          assets: ['scripts/**.js', 'styles/**.css'],
          deleteOriginals: true
        },
        src: ['<%= yeoman.dist %>/index.html']
      }
    },

    googlefonts: {
      build: {
        options: {
          fontPath: '<%= yeoman.app %>/fonts/',
          httpPath: '../fonts/',
          cssFile: '<%= yeoman.app %>/fonts/google-fonts.css',
          formats: {
            eot: true,
            ttf: true,
            woff: true,
            svg: true
          },
          fonts: [
            {
              family: 'Patua One',
              styles: [400]
            },
            {
              family: 'Source Code Pro',
              styles: [300, 400, 500]
            },
            {
              family: 'Roboto',
              styles: [300, 400, 500]
            }
          ]
        }
      }
    },

    // Watches files for changes and runs tasks based on the changed files
    watch: {
      bower: {
        files: ['bower.json'],
        tasks: ['wiredep']
      },
      js: {
        files: [
          '<%= yeoman.app %>/app/**/*.js',
          '<%= yeoman.app %>/components/**/*.js'
        ],
        tasks: ['newer:eslint:all', 'newer:jscs:all', 'newer:babel:dev'],
        options: {
          livereload: '<%= connect.options.livereload %>'
        }
      },
      html: {
        files: [
          '<%= yeoman.app %>/**/*.html'
        ],
        tasks: ['newer:htmlhint', 'newer:copy:dev']
      },
      jsTest: {
        files: ['test/spec/{,*/}*.js'],
        tasks: [
          'newer:eslint:test',
          'newer:jscs:test',
          'newer:babel:dev',
          'karma'
        ]
      },
      styles: {
        files: [
          '<%= yeoman.app %>/app/**/*.css',
          '<%= yeoman.app %>/components/**/*.css',
          '<%= yeoman.app %>/assets/styles/**/*.css',
          '<%= yeoman.app %>/fonts/**/*.css'
        ],
        tasks: ['newer:copy:styles', 'postcss']
      },
      gruntfile: {
        files: ['Gruntfile.js']
      },
      livereload: {
        options: {
          livereload: '<%= connect.options.livereload %>'
        },
        files: [
          '<%= yeoman.app %>/app/**/*.html',
          '<%= yeoman.app %>/*.html',
          '<%= yeoman.app %>/components/**/*.html',
          '.tmp/styles/{,*/}*.css',
          '<%= yeoman.app %>/assets/images/**/*.{png,jpg,jpeg,gif,webp,svg}'
        ]
      }
    },

    // The actual grunt server settings
    connect: {
      options: {
        port: 9000,
        // Change this to '0.0.0.0' to access the server from outside.
        hostname: 'localhost',
        livereload: 35729,
        base: '.tmp',
      },
      livereload: {
        options: {
          open: false,
          middleware: function(connect) {
            return [
              connect.static('.tmp'),
              connect().use(
                '/bower_components',
                connect.static('./bower_components')
              ),
            ];
          }
        }
      },
      test: {
        options: {
          port: 9001,
          middleware: function(connect) {
            return [
              connect.static('.tmp'),
              connect.static('test'),
              connect().use(
                '/bower_components',
                connect.static('./bower_components')
              ),
              connect.static(appConfig.app)
            ];
          }
        }
      },
      dist: {
        options: {
          open: false,
          base: '<%= yeoman.dist %>'
        }
      }
    },

    jscs: {
      options: {
        config: '.jscsrc',
        esnext: true, // If you use ES6 http://jscs.info/overview.html#esnext
        verbose: true, // If you need output with rule names http://jscs.info/overview.html#verbose
        requireCurlyBraces: ['if']
      },
      all: {
        src: [
          'Gruntfile.js',
          '<%= yeoman.app %>/app/**/*.js',
          '<%= yeoman.app %>/components/**/*.js'
        ]
      },
      test: {
        src: ['test/spec/{,*/}*.js']
      }
    },

    eslint: {
      all: {
        src: [
          'Gruntfile.js',
          '<%= yeoman.app %>/app/**/*.js',
          '<%= yeoman.app %>/components/**/*.js'
        ]
      },
      test: {
        options: {
          rules: {
            'no-undef': 0
          }
        },
        src: ['test/spec/{,*/}*.js']
      }
    },

    // Empties folders to start fresh
    clean: {
      dist: {
        files: [{
          dot: true,
          src: [
            '.tmp',
            '<%= yeoman.dist %>/{,*/}*',
            '!<%= yeoman.dist %>/.git*'
          ]
        }]
      },
      server: '.tmp'
    },

    // Add vendor prefixed styles
    postcss: {
      options: {
        map: true,
        processors: [
          require('autoprefixer')({browsers: ['last 2 versions']})
        ]
      },
      dist: {
        files: [{
          expand: true,
          cwd: '.tmp/styles/',
          src: '{,*/}*.css',
          dest: '.tmp/styles/'
        }]
      }
    },

    // Automatically inject Bower components into the app
    wiredep: {
      options: {},
      app: {
        src: ['<%= yeoman.app %>/index.html'],
        ignorePath: /\.\.\//
      },
      test: {
        devDependencies: true,
        src: '<%= karma.unit.configFile %>',
        ignorePath: /\.\.\//,
        fileTypes: {
          js: {
            block: /(([\s\t]*)\/{2}\s*?bower:\s*?(\S*))(\n|\r|.)*?(\/{2}\s*endbower)/gi,
            detect: {
              js: /'(.*\.js)'/gi
            },
            replace: {
              js: '\'{{filePath}}\','
            }
          }
        }
      }
    },

    // Reads HTML for usemin blocks to enable smart builds that automatically
    // concat, minify and revision files. Creates configurations in memory so
    // additional tasks can operate on them
    useminPrepare: {
      html: '<%= yeoman.app %>/index.html',
      options: {
        dest: '<%= yeoman.dist %>',
        flow: {
          html: {
            steps: {
              js: ['concat', 'uglifyjs'],
              css: ['cssmin']
            },
            post: {}
          }
        }
      }
    },

    // Performs rewrites based on filerev and the useminPrepare configuration
    usemin: {
      html: ['<%= yeoman.dist %>/{,*/}*.html'],
      css: ['<%= yeoman.dist %>/styles/*.css'],
      options: {
        assetsDirs: ['<%= yeoman.dist %>', '<%= yeoman.dist %>/assets']
      }
    },

    // The following *-min tasks will produce minified files in the dist folder
    // By default, your `index.html`'s <!-- Usemin block --> will take care of
    // minification. These next options are pre-configured if you do not wish
    // to use the Usemin blocks.
    cssmin: {
      dist: {
        files: {
          '<%= yeoman.dist %>/styles/main.css': [
            '.tmp/styles/*.css'
          ]
        }
      }
    },

    uglify: {
      options: {
        mangle: {
          'screw_ie8': true
        },
        preserveComments: 'some',
        compress: {
          'screw_ie8': true,
          sequences: true,
          'dead_code': true,
          conditionals: true,
          booleans: true,
          unused: true,
          'if_return': true,
          'join_vars': true,
          'drop_console': true
        }
      }
    },

    svgmin: {
      dist: {
        files: [{
          expand: true,
          cwd: '<%= yeoman.app %>/assets/images',
          src: '{,*/}*.svg',
          dest: '<%= yeoman.dist %>/assets/images'
        }]
      }
    },

    htmlmin: {
      dist: {
        options: {
          collapseWhitespace: true,
          conservativeCollapse: true,
          collapseBooleanAttributes: true,
          removeCommentsFromCDATA: true,
          removeOptionalTags: true
        },
        files: [{
          expand: true,
          cwd: '<%= yeoman.dist %>',
          src: [
            '*.html',
            'app/**/*.html',
            'components/**/*.html'
          ],
          dest: '<%= yeoman.dist %>'
        }]
      }
    },

    // Copies remaining files to places other tasks can use
    copy: {
      dev: {
        files: [{
          expand: true,
          dot: true,
          cwd: '<%= yeoman.app %>',
          dest: '.tmp',
          src: [
            '*.{ico,png,txt}',
            '.htaccess',
            '*.html',
            '**/*.css',
            'assets/styles/**/*',
            'assets/images/**/*',
            'WEB-INF/*'
          ]
        }, {
          // copy fonts
          expand: true,
          cwd: '<%= yeoman.app %>',
          dest: '.tmp',
          src: ['fonts/**/*.{eot,svg,ttf,woff}']
        }, {
          expand: true,
          cwd: '<%= yeoman.app %>',
          dest: '.tmp',
          src: ['app/**/*.html', 'components/**/*.html']
        }, {
          expand: true,
          cwd: 'bower_components/datatables/media/images',
          src: '{,*/}*.{png,jpg,jpeg,gif}',
          dest: '.tmp/images'
        }, {
          expand: true,
          cwd: '.tmp/images',
          dest: '.tmp/images',
          src: ['generated/*']
        }, {
          expand: true,
          cwd: 'bower_components/bootstrap/dist',
          src: 'fonts/*',
          dest: '.tmp'
        }, {
          expand: true,
          cwd: 'bower_components/jquery-ui/themes/base/images',
          src: '{,*/}*.{png,jpg,jpeg,gif}',
          dest: '.tmp/styles/images'
        }, {
          expand: true,
          cwd: 'bower_components/ngclipboard',
          src: 'dist/**',
          dest: '.tmp'
        }, {
          expand: true,
          cwd: 'bower_components/MathJax',
          src: [
            'extensions/**', 'jax/**', 'fonts/**'],
          dest: '.tmp'
        }]
      },
      dist: {
        files: [{
          expand: true,
          dot: true,
          cwd: '<%= yeoman.app %>',
          dest: '<%= yeoman.dist %>',
          src: [
            '*.{ico,png,txt}',
            '.htaccess',
            '*.html',
            'assets/styles/**/*',
            'assets/images/**/*',
            'WEB-INF/*'
          ]
        }, {
          // copy fonts
          expand: true,
          cwd: '<%= yeoman.app %>',
          dest: '<%= yeoman.dist %>',
          src: ['fonts/**/*.{eot,svg,ttf,woff}']
        }, {
          expand: true,
          cwd: '<%= yeoman.app %>',
          dest: '<%= yeoman.dist %>',
          src: ['app/**/*.html', 'components/**/*.html']
        }, {
          expand: true,
          cwd: 'bower_components/datatables/media/images',
          src: '{,*/}*.{png,jpg,jpeg,gif}',
          dest: '<%= yeoman.dist %>/images'
        }, {
          expand: true,
          cwd: '.tmp/images',
          dest: '<%= yeoman.dist %>/images',
          src: ['generated/*']
        }, {
          expand: true,
          cwd: 'bower_components/bootstrap/dist',
          src: 'fonts/*',
          dest: '<%= yeoman.dist %>'
        }, {
          expand: true,
          cwd: 'bower_components/jquery-ui/themes/base/images',
          src: '{,*/}*.{png,jpg,jpeg,gif}',
          dest: '<%= yeoman.dist %>/styles/images'
        }, {
          expand: true,
          cwd: 'bower_components/ngclipboard',
          src: 'dist/**',
          dest: '<%= yeoman.dist %>'
        }, {
          expand: true,
          cwd: 'bower_components/MathJax',
          src: [
            'extensions/**', 'jax/**', 'fonts/**'],
          dest: '<%= yeoman.dist %>'
        }]
      },
      styles: {
        expand: true,
        flatten: true,
        cwd: '<%= yeoman.app %>',
        dest: '.tmp/styles/',
        src: '{fonts,components,app}/**/*.css'
      }
    },

    // Run some tasks in parallel to speed up the build process
    concurrent: {
      server: [
        'copy:dev'
      ],
      test: [
        'copy:dev',
      ],
      dist: [
        'copy:styles',
        'svgmin'
      ]
    },

    // Test settings
    karma: {
      unit: {
        configFile: 'test/karma.conf.js',
        singleRun: true
      }
    }
  });

  grunt.registerTask('serve', 'Compile then start a connect web server', function(target) {
    if (target === 'dist') {
      return grunt.task.run(['build', 'connect:dist:keepalive']);
    }

    grunt.task.run([
      'clean:server',
      'wiredep',
      'concurrent:server',
      'postcss',
      'babel:dev',
      'connect:livereload',
      'watch'
    ]);
  });

  grunt.registerTask('server', 'DEPRECATED TASK. Use the "serve" task instead', function(target) {
    grunt.log.warn('The `server` task has been deprecated. Use `grunt serve` to start a server.');
    grunt.task.run(['serve:' + target]);
  });

  grunt.registerTask('test', [
    'clean:server',
    'babel',
    'wiredep',
    'concurrent:test',
    'postcss',
    'babel:dev',
    'connect:test',
    'karma'
  ]);

  grunt.registerTask('build', [
    'jscs',
    'eslint',
    'htmlhint',
    'clean:dist',
    'wiredep',
    'useminPrepare',
    'concurrent:dist',
    'postcss',
    'concat',
    'babel:dist',
    'ngAnnotate',
    'copy:dist',
    'cssmin',
    'uglify',
    'usemin',
    'htmlmin',
    'cacheBust'
  ]);

  grunt.registerTask('default', [
    'build',
    'test'
  ]);
};
