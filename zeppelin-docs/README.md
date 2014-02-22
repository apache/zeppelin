# Zeppelin documentation
Build Zeppelin documentation

## Get Sphinx-doc
 - Install [Sphinx](http://sphinx-doc.org/install.html)

## Build
```mvn package```
Or
```make html```

Sphinx will create the folder "targert".

### Build failed
 - make: sphinx-build: No such file or directory
```
Make sure sphinx is correctly installed
Check the sphinx app name (can be sphinx-build27, in that case make a symbolic link)
```
 - ValueError: unknown locale: UTF-8
````
export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8
```
