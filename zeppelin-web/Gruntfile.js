  ]);

  grunt.registerTask('build', [
    'test',
    'clean:dist',
    'wiredep',
    'useminPrepare',
    'concurrent:dist',
    'autoprefixer',
    'concat',
    'ngAnnotate',
    'copy:dist',
    'cssmin',
    'uglify',
    'usemin',
    'htmlmin'
  ]);

  grunt.registerTask('buildSkipTests', [
    'clean:dist',
    'wiredep',
    'useminPrepare',
