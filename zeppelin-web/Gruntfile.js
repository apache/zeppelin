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

module.exports = function(grunt) {

  // Load grunt tasks automatically
  require('load-grunt-tasks')(grunt);

  // Time how long tasks take. Can help when optimizing build times
  require('time-grunt')(grunt);

  require('grunt-replace')(grunt);

  // Configurable paths for the application
  var appConfig = {
    app: require('./bower.json').appPath || 'src',
    dist: 'dist'
  };

  var buildtime = Date.now();

  // Define the configuration for all the tasks
  grunt.initConfig({

    // Project settings
    yeoman: appConfig,

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

    // Watches files for changes and runs tasks based on the changed files
    watch: {
      bower: {
        files: ['bower.json'],
        tasks: ['wiredep:dist', 'wiredep:test']
      },
      html: {
        files: [
          '<%= yeoman.app %>/**/*.html'
        ],
        tasks: ['newer:htmlhint']
      },
      jsTest: {
        files: ['test/spec/{,*/}*.js'],
        tasks: [
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
        options: {livereload: 35729,},
        files: [
          '<%= yeoman.app %>/app/**/*.html',
          '<%= yeoman.app %>/*.html',
          '<%= yeoman.app %>/components/**/*.html',
          '<%= yeoman.app %>/**/*.css',
          '<%= yeoman.app %>/assets/images/**/*.{png,jpg,jpeg,gif,webp,svg}'
        ]
      }
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
      ci: {
        src: ['<%= yeoman.app %>/index.html'],
        ignorePath: /\.\.\//,
        exclude: [
        ]
      },
      dist: {
        src: ['<%= yeoman.app %>/index.html'],
        ignorePath: /\.\.\//,
        exclude: [
        ],
      },
      test: {
        devDependencies: true,
        src: '<%= karma.unit.configFile %>',
        ignorePath: /\.\.\//,
        exclude: [
        ],
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
      html: '<%= yeoman.dist %>/index.html',
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
      dist: {
        files: [{
          expand: true,
          dot: true,
          cwd: '<%= yeoman.app %>',
          dest: '<%= yeoman.dist %>',
          src: [
            '*.{ico,png,txt}',
            '.htaccess',
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
      },
      html: {
        files: [{
          expand: true,
          dot: true,
          cwd: '<%= yeoman.app %>',
          dest: '.tmp',
          src: ['*.html']
        }]
      },
    },

    // Run some tasks in parallel to speed up the build process
    concurrent: {
      dist: [
        'copy:styles',
        'svgmin'
      ],
    },

    replace: {
      dist: {
        options: {
          patterns: [
            {
              match: /(templateUrl:"[^\.\s]+\.html)/g,
              replacement: '$1' + '?v=' + buildtime
            },
            {
              match: /(ng-include src="'[^\.\s]+\.html)/g,
              replacement: '$1' + '?v=' + buildtime
            }
          ]
        },
        files: [
          {src: ['dist/**/*.html'], dest: './'},
          {src: ['dist/*.js'], dest: './'}
        ]
      }
    },

    // Test settings
    karma: {
      unit: {
        configFile: 'karma.conf.js',
        singleRun: true
      }
    }
  });

  grunt.registerTask('pre-webpack-dev', 'Compile then start a connect web server', function(target) {
    grunt.task.run([
      'wiredep:test',
      'wiredep:dist',
    ]);
  });

  grunt.registerTask('watch-webpack-dev', [
    'watch',
  ]);

  grunt.registerTask('pre-webpack-dist', [
    'htmlhint',
    'wiredep:test',
    'wiredep:dist',
  ]);

  grunt.registerTask('pre-webpack-ci', [
    'htmlhint',
    'wiredep:test',
    'wiredep:ci',
  ]);

  grunt.registerTask('post-webpack-dist', [
    'useminPrepare',
    'concurrent:dist',
    'postcss',
    'concat',
    'ngAnnotate',
    'copy:dist',
    'cssmin',
    'uglify',
    'usemin',
    'htmlmin',
    'replace',
    'cacheBust',
  ]);

  grunt.registerTask('default', [
    'build'
  ]);
};
