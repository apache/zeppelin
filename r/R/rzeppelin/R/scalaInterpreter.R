rzeppelinPackage <- function(pkgname) {
  environmentOfDependingPackage <- parent.env(parent.frame())
  E <- new.env(parent=environmentOfDependingPackage)
  E$initialized <- FALSE
  E$pkgname <- pkgname
  assign("E",E,envir=environmentOfDependingPackage)
  invisible()
}



# Private

checkType <- function(x) {
  if ( is.integer(x) ) INTEGER
  else if ( is.double(x) ) DOUBLE
  else if ( is.logical(x) ) BOOLEAN
  else if ( is.character(x) ) STRING
  else if ( is.date(x)) DATE
  else stop("Unsupported data type.")
}

checkType2 <- function(x) {
  if ( is.integer(x) ) "Int"
  else if ( is.double(x) ) "Double"
  else if ( is.logical(x) ) "Boolean"
  else if ( is.character(x) ) "String"
  else if ( is.date(x) ) "Date"
  else stop("Unsupported data type.")
}

convert <- function(x,t) {
  if ( t == "Int" ) {
    tt <- "atomic"
    tm <- "integer"
    loav <- FALSE
  } else if ( t == "Double" ) {
    tt <- "atomic"
    tm <- "double"
    loav <- FALSE
  } else if ( t == "Boolean" ) {
    tt <- "atomic"
    tm <- "logical"
    loav <- FALSE
  } else if ( t == "String" ) {
    tt <- "atomic"
    tm <- "character"
    loav <- FALSE
  } else if ( t == "Array[Int]" ) {
    tt <- "vector"
    tm <- "integer"
    loav <- TRUE
  } else if ( t == "Array[Double]" ) {
    tt <- "vector"
    tm <- "double"
    loav <- TRUE
  } else if ( t == "Array[Boolean]" ) {
    tt <- "vector"
    tm <- "logical"
    loav <- TRUE
  } else if ( t == "Array[String]" ) {
    tt <- "vector"
    tm <- "character"
    loav <- TRUE
  } else if ( t == "Array[Array[Int]]" ) {
    tt <- "matrix"
    tm <- "integer"
    loav <- TRUE
  } else if ( t == "Array[Array[Double]]" ) {
    tt <- "matrix"
    tm <- "double"
    loav <- TRUE
  } else if ( t == "Array[Array[Boolean]]" ) {
    tt <- "matrix"
    tm <- "logical"
    loav <- TRUE
  } else if ( t == "Array[Array[String]]" ) {
    tt <- "matrix"
    tm <- "character"
    loav <- TRUE
  } else {
    tt <- "reference"
    tm <- "reference"
    loav <- FALSE
  }
  v <- character(0)
  if ( tt == "atomic" ) v <- c(v,sprintf("%s <- as.vector(%s)[1]",x,x))
  else if ( tt == "vector" ) v <- c(v,sprintf("%s <- as.vector(%s)",x,x))
  else if ( tt == "matrix" ) v <- c(v,sprintf("%s <- as.matrix(%s)",x,x))
  if ( tm != "reference" ) v <- c(v,sprintf("storage.mode(%s) <- '%s'",x,tm))
  if ( length(v) != 0 ) {
    v <- c(sprintf("if ( ! inherits(%s,'ScalaInterpreterReference') ) {",x),paste("  ",v,sep=""),"}")
  }
  c(v,sprintf("intpSet(interpreter,'.',%s,length.one.as.vector=%s,quiet=TRUE)",x,loav))
}

cc <- function(c) {
  if ( ! get("open",envir=c[['env']]) ) stop("The connection has already been closed.")
}

wb <- function(c,v) writeBin(v,c[['socketIn']],endian="big")

wc <- function(c,v) {
  bytes <- charToRaw(v)
  wb(c,length(bytes))
  writeBin(bytes,c[['socketIn']],endian="big",useBytes=TRUE)
}

# Sockets should be blocking, but that contract is not fulfilled when other code uses functions from the parallel library.  Program around their problem.
rb <- function(c,v,n=1L) {
  r <- readBin(c[['socketOut']],what=v,n=n,endian="big")
  if ( length(r) == n ) r
  else c(r,rb(c,v,n-length(r)))
}

# Sockets should be blocking, but that contract is not fulfilled when other code uses functions from the parallel library.  Program around their problem.
rc <- function(c) {
  length <- rb(c,integer(0))
  r <- as.raw(c())
  while ( length(r) != length ) r <- c(r,readBin(c[['socketOut']],what="raw",n=length,endian="big"))
  rawToChar(r)
}

