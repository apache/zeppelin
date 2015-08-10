## Scala scripting over TCP/IP

scalaInterpreter <- function(classpath=character(0),scala.home=NULL,java.home=NULL,java.heap.maximum="256M",java.opts=NULL,timeout=60,debug.filename=NULL) {
  debug <- !is.null(debug.filename)
  userJars <- unlist(strsplit(classpath,.Platform$path.sep))
  if ( is.null(java.opts) ) {
    java.opts <- c(paste("-Xmx",java.heap.maximum,sep=""),"-Xms32M")
  }
  sInfo <- scalaInfo(scala.home)
  if ( is.null(sInfo) ) stop("Cannot find a suitable Scala installation.  Please manually install Scala or run 'scalaInstall()'.")
  rsJar <- rscalaJar(sInfo$version)
  rsClasspath <- shQuote(paste(c(rsJar,userJars),collapse=.Platform$path.sep))
  args <- c(
    java.opts,
    paste("-Xbootclasspath/a:",shQuote(paste(c(rsJar,sInfo$jars),collapse=.Platform$path.sep)),sep=""),
    "-classpath",
    '""',
    paste("-Dscala.home=",shQuote(sInfo$home),sep=""),
    "-Dscala.usejavacp=true",
    "-Denv.emacs=",
    paste("-Drscala.classpath=",rsClasspath,sep=""),
    "scala.tools.nsc.MainGenericRunner",
    "-howtorun:script","-Xnojline",
    "-classpath",rsClasspath)
  if ( debug ) {
    cat("R DEBUG:\n")
    cat("Command line:\n")
    cat(paste("<",args,">",sep="",collapse="\n"),"\n",sep="")
  }
  portsFilename <- tempfile("rscala-")
  Sys.setenv(RSCALA_TUNNELING="TRUE")
  bootstrap.filename <- tempfile("rscala-")
  bootstrap.file <- file(bootstrap.filename, "w")
  writeLines(c(sprintf('ScalaServer(ScalaInterpreterAdapter($intp),raw"%s").run()',portsFilename),'sys.exit(0)'),bootstrap.file)
  close(bootstrap.file)
  stdout <- ifelse(!is.null(debug.filename),sprintf("%s-stdout",debug.filename),FALSE)
  stderr <- ifelse(!is.null(debug.filename),sprintf("%s-stderr",debug.filename),FALSE)
  system2(javaCmd(java.home),args,wait=FALSE,stdin=bootstrap.filename,stdout=stdout,stderr=stderr)
  sockets <- newSockets(portsFilename,debug,timeout)
  sockets[['scalaInfo']] <- sInfo
  assign("debug",!debug,envir=sockets[['env']])
  assign("markedForGC",integer(0),envir=sockets[['env']])
  intpSettings(sockets,debug=debug,interpolate=TRUE,length.one.as.vector=FALSE,quiet=FALSE)
  sockets
}

newSockets <- function(portsFilename,debug,timeout) {
  getPortNumbers <- function() {
    delay <- 0.1
    start <- proc.time()[3]
    while ( TRUE ) {
      if ( ( proc.time()[3] - start ) > timeout ) stop("Timed out waiting for Scala to start.")
      Sys.sleep(delay)
      delay <- 1.0*delay
      if ( file.exists(portsFilename) ) {
        line <- scan(portsFilename,n=2,what=character(0),quiet=TRUE)
        if ( length(line) > 0 ) return(as.numeric(line))
      }
    }
  }
  ports <- getPortNumbers()
  file.remove(portsFilename)
  if ( debug ) cat("R DEBUG: Trying to connect to port:",paste(ports,collapse=","),"\n")
  socketConnectionIn  <- socketConnection(port=ports[1],blocking=TRUE,open="ab",timeout=2678400)
  socketConnectionOut <- socketConnection(port=ports[2],blocking=TRUE,open="rb",timeout=2678400)
  functionCache <- new.env()
  env <- new.env()
  assign("open",TRUE,envir=env)
  assign("debug",debug,envir=env)
  assign("length.one.as.vector",FALSE,envir=env)
  workspace <- new.env()
  workspace$. <- new.env(parent=workspace)
  result <- list(socketIn=socketConnectionIn,socketOut=socketConnectionOut,env=env,workspace=workspace,functionCache=functionCache)
  class(result) <- "ScalaInterpreter"
  status <- rb(result,integer(0))
  if ( ( length(status) == 0 ) || ( status != OK ) ) stop("Error instantiating interpreter.")
  wc(result,toString(packageVersion("rscala")))
  flush(result[['socketIn']])
  result
}

intpEval.ScalaInterpreter <- function(interpreter,snippet,interpolate="",quiet="") {
  cc(interpreter)
  snippet <- paste(snippet,collapse="\n")
  if ( ( ( interpolate == "" ) && ( get("interpolate",envir=interpreter[['env']]) ) ) || ( interpolate == TRUE ) ) {
    snippet <- strintrplt(snippet,parent.frame())
  }
  wb(interpreter,EVAL)
  wc(interpreter,snippet)
  flush(interpreter[['socketIn']])
  rServe(interpreter)
  status <- rb(interpreter,integer(0))
  echoResponseScala(interpreter,quiet)
  invisible(NULL)
}

'%~%.ScalaInterpreter' <- function(interpreter,snippet) {
  cc(interpreter)
  snippet <- paste(snippet,collapse="\n")
  if ( get("interpolate",envir=interpreter[['env']]) ) {
    snippet <- strintrplt(snippet,parent.frame())
  }
  result <- evalAndGet(interpreter,snippet,NA)
  if ( is.null(result) ) invisible(result)
  else result
}

'%.~%.ScalaInterpreter' <- function(interpreter,snippet) {
  cc(interpreter)
  snippet <- paste(snippet,collapse="\n")
  if ( get("interpolate",envir=interpreter[['env']]) ) {
    snippet <- strintrplt(snippet,parent.frame())
  }
  result <- evalAndGet(interpreter,snippet,TRUE)
  if ( is.null(result) ) invisible(result)
  else result
}

print.ScalaInterpreter <- function(x,...) {
  cat("ScalaInterpreter\n")
  invisible(x)
}

toString.ScalaInterpreter <- function(x,...) {
  "ScalaInterpreter"
}

print.ScalaInterpreterReference <- function(x,...) {
  type <- if ( substring(x[['type']],1,2) == "()" ) substring(x[['type']],3)
  else x[['type']]
  cat("ScalaInterpreterReference... ")
  if ( is.null(x[['env']]) ) {
    cat(x[['identifier']],": ",type,"\n",sep="")
  } else {
    cat("*: ",type,"\n",sep="")
  }
  invisible(x)
}

toString.ScalaInterpreterReference <- function(x,...) {
  if ( is.null(x[['env']]) ) x[['identifier']]
  else ""
}

print.ScalaInterpreterItem <- function(x,...) {
  cat("ScalaInterpreterItem\n")
  invisible(x)
}

toString.ScalaInterpreterItem <- function(x,...) {
  "ScalaInterpreterItem"
}

"$.ScalaInterpreterReference" <- function(reference,methodName) {
  type <- reference[['type']]
  functionCache <- reference[['interpreter']][['functionCache']]
  env <- reference[['interpreter']][['env']]
  function(...,evaluate=TRUE,length.one.as.vector="",as.reference=NA,quiet="",gc=FALSE) {
    loav <- ! ( ( ( length.one.as.vector == "" ) && ( ! get("length.one.as.vector",envir=env) ) ) || length.one.as.vector == FALSE )
    args <- list(...)
    nArgs <- length(args)
    names <- paste0("x",1:nArgs)
    types <- sapply(args,deduceType,length.one.as.vector=loav)
    argsStr <- paste(names,types,sep=": ",collapse=", ")
    namesStr <- paste(names,collapse=", ")
    key <- paste0(type,"$",methodName,"#",paste(types,collapse=","))
    if ( ! exists(key,envir=functionCache) ) {
      f <- if ( nArgs == 0 ) {
        intpDef(reference[['interpreter']],'',paste0('rscalaReference.',methodName),reference=reference)
      } else {
        intpDef(reference[['interpreter']],argsStr,paste0('rscalaReference.',methodName,"(",namesStr,")"),reference=reference)
      }
      assign(key,f,envir=functionCache)
    } else {
      f <- get(key,envir=functionCache)
      assign("rscalaReference",reference,envir=environment(f),inherits=TRUE)
    }
    if ( evaluate ) f(...,as.reference=as.reference,quiet=quiet,gc=gc)
    else f
  }
}

"$.ScalaInterpreterItem" <- function(item,methodName) {
  interpreter <- item[['interpreter']]
  type <- item[['item.name']]
  functionCache <- interpreter[['functionCache']]
  env <- interpreter[['env']]
  function(...,evaluate=TRUE,length.one.as.vector="",as.reference=NA,quiet="",gc=FALSE) {
    loav <- ! ( ( ( length.one.as.vector == "" ) && ( ! get("length.one.as.vector",envir=env) ) ) || length.one.as.vector == FALSE )
    args <- list(...)
    nArgs <- length(args)
    names <- paste0("x",1:nArgs)
    types <- sapply(args,deduceType,length.one.as.vector=loav)
    argsStr <- paste(names,types,sep=": ",collapse=", ")
    namesStr <- paste(names,collapse=", ")
    key <- paste0(type,"@",methodName,"#",paste(types,collapse=","))
    if ( ! exists(key,envir=functionCache) ) {
      f <- if ( nArgs == 0 ) {
        if ( methodName == "new" ) {
          intpDef(interpreter,'',paste0('new ',type))
        } else {
          intpDef(interpreter,'',paste0(type,'.',methodName))
        }
      } else {
        if ( methodName == "new" ) {
          intpDef(interpreter,argsStr,paste0('new ',type,"(",namesStr,")"))
        } else {
          intpDef(interpreter,argsStr,paste0(type,'.',methodName,"(",namesStr,")"))
        }
      }
      assign(key,f,envir=functionCache)
    } else {
      f <- get(key,envir=functionCache)
    }
    if ( evaluate ) f(...,as.reference=as.reference,quiet=quiet,gc=gc)
    else f
  }
}

intpGet.ScalaInterpreter <- function(interpreter,identifier,as.reference=NA) {
  cc(interpreter)
  if ( ( ! is.na(as.reference) ) && ( as.reference ) ) {
    if ( inherits(identifier,"ScalaInterpreterReference") ) return(identifier)
    wb(interpreter,GET_REFERENCE)
    wc(interpreter,as.character(identifier[1]))
    flush(interpreter[['socketIn']])
    response <- rb(interpreter,integer(0))
    if ( response == OK ) {
      id <- rc(interpreter)
      type <- rc(interpreter)
      env <- if ( substr(id,1,1) == "." ) {
        env <- new.env()
        reg.finalizer(env,function(e) {
          assign("markedForGC",c(as.integer(substring(id,2)),get("markedForGC",interpreter[['env']])),interpreter[['env']])
        })
        env
      } else NULL
      result <- list(interpreter=interpreter,identifier=id,type=type,env=env)
      class(result) <- "ScalaInterpreterReference"
      return(result)
    } else if ( response == ERROR ) {
      echoResponseScala(interpreter,FALSE)
      stop(paste("Exception thrown.",sep=""))
    } else if ( response == UNDEFINED_IDENTIFIER ) {
      stop(paste("Undefined identifier: ",identifier[1],sep=""))
    } else stop("Protocol error.")
  }
  i <- if ( inherits(identifier,"ScalaInterpreterReference") ) identifier[['identifier']] else as.character(identifier[1])
  wb(interpreter,GET)
  wc(interpreter,i)
  flush(interpreter[['socketIn']])
  dataStructure <- rb(interpreter,integer(0))
  if ( dataStructure == NULLTYPE ) {
    NULL
  } else if ( dataStructure == ATOMIC ) {
    dataType <- rb(interpreter,integer(0))
    if ( dataType == STRING ) {
      rc(interpreter)
    } else {
      v <- rb(interpreter,typeMap[[dataType]])
      if ( dataType == BOOLEAN ) as.logical(v)
      else v
    }
  } else if ( dataStructure == VECTOR ) {
    length <- rb(interpreter,integer(0))
    dataType <- rb(interpreter,integer(0))
    if ( dataType == STRING ) {
      if ( length > 0 ) sapply(1:length,function(x) rc(interpreter))
      else character(0)
    } else {
      v <- rb(interpreter,typeMap[[dataType]],length)
      if ( dataType == BOOLEAN ) as.logical(v)
      else v
    }
  } else if ( dataStructure == MATRIX ) {
    dim <- rb(interpreter,integer(0),2L)
    dataType <- rb(interpreter,integer(0))
    if ( dataType == STRING ) {
      v <- matrix("",nrow=dim[1],ncol=dim[2])
      if ( dim[1] > 0 ) for ( i in 1:dim[1] ) {
        if ( dim[2] > 0 ) for ( j in 1:dim[2] ) {
          v[i,j] <- rc(interpreter)
        }
      }
      v
    } else {
      v <- matrix(rb(interpreter,typeMap[[dataType]],dim[1]*dim[2]),nrow=dim[1],byrow=TRUE)
      if ( dataType == BOOLEAN ) mode(v) <- "logical"
      v
    }
  } else if ( dataStructure == REFERENCE ) {
    itemName <- rc(interpreter)
    get(itemName,envir=interpreter[['workspace']]$.)
  } else if ( dataStructure == ERROR ) {
    echoResponseScala(interpreter,FALSE)
    stop(paste("Exception thrown.",sep=""))
  } else if ( dataStructure == UNDEFINED_IDENTIFIER ) {
    echoResponseScala(interpreter,TRUE)
    stop(paste("Undefined identifier: ",i,sep=""))
  } else if ( dataStructure == UNSUPPORTED_STRUCTURE ) {
    if ( is.na(as.reference) ) {
      intpGet(interpreter,identifier,as.reference=TRUE)
    } else {
      stop("Unsupported data structure.")
    }
  } else stop("Protocol error.")
}

'$.ScalaInterpreter' <- function(interpreter,identifier) {
  cc(interpreter)
  if ( identifier == "def" ) function(args,body) {
    body <- paste(body,collapse="\n")
    if ( get("interpolate",envir=interpreter[['env']]) ) {
      args <- strintrplt(args,parent.frame())
      body <- strintrplt(body,parent.frame())
    }
    intpDef(interpreter,args,body,interpolate=FALSE)
  } else if ( identifier == "do" ) function(item.name) {
    result <- list(interpreter=interpreter,item.name=item.name)
    class(result) <- "ScalaInterpreterItem"
    result
  } else if ( identifier == "val" ) function(x) {
    intpGet(interpreter,x)
  } else if ( identifier == ".val" ) function(x) {
    intpGet(interpreter,x,as.reference=TRUE)
  } else if ( substring(identifier,1,1) == "." ) {
    identifier = substring(identifier,2)
    if ( identifier == "" ) intpGet(interpreter,".")
    else if ( identifier == "." ) intpGet(interpreter,".",as.reference=TRUE)
    else intpGet(interpreter,identifier,as.reference=TRUE)
  } else {
    intpGet(interpreter,identifier)
  }
}

deduceType <- function(value,length.one.as.vector) {
  if ( inherits(value,"ScalaInterpreterReference") ) value[['type']]
  else {
    if ( ! is.atomic(value) ) stop("Data structure is not supported.")
    if ( is.vector(value) ) {
      type <- checkType2(value)
      if ( ( length(value) == 1 ) && ( ! length.one.as.vector ) ) type
      else paste0("Array[",type,"]")
    } else if ( is.matrix(value) ) paste0("Array[Array[",checkType2(value),"]]")
    else if ( is.null(value) ) "Any"
    else stop("Data structure is not supported.")
  }
}

intpSet.ScalaInterpreter <- function(interpreter,identifier,value,length.one.as.vector="",quiet="") {
  cc(interpreter)
  if ( inherits(value,"ScalaInterpreterReference") ) {
    wb(interpreter,SET)
    wc(interpreter,identifier)
    wb(interpreter,REFERENCE)
    wc(interpreter,value[['identifier']])
    flush(interpreter[['socketIn']])
    status <- rb(interpreter,integer(0))
    echoResponseScala(interpreter,quiet)
    if ( status == ERROR ) {
      stop("Setting error.")
    }
  } else {
    if ( ! is.atomic(value) ) stop("Data structure is not supported.")
    if ( is.vector(value) ) {
      type <- checkType(value)
      wb(interpreter,SET)
      wc(interpreter,identifier)
      if ( ( length(value) == 1 ) && ( ( ( length.one.as.vector == "" ) && ( ! get("length.one.as.vector",envir=interpreter[['env']]) ) ) || length.one.as.vector == FALSE ) ) {
        wb(interpreter,ATOMIC)
      } else {
        wb(interpreter,VECTOR)
        wb(interpreter,length(value))
      }
      wb(interpreter,type)
      if ( type == STRING ) {
        if ( length(value) > 0 ) for ( i in 1:length(value) ) wc(interpreter,value[i])
      } else {
        if ( type == BOOLEAN ) wb(interpreter,as.integer(value))
        else wb(interpreter,value)
      }
    } else if ( is.matrix(value) ) {
      type <- checkType(value)
      wb(interpreter,SET)
      wc(interpreter,identifier)
      wb(interpreter,MATRIX)
      wb(interpreter,dim(value))
      wb(interpreter,type)
      if ( nrow(value) > 0 ) for ( i in 1:nrow(value) ) {
        if ( type == STRING ) {
          if ( ncol(value) > 0 ) for ( j in 1:ncol(value) ) wc(interpreter,value[i,j])
        }
        else if ( type == BOOLEAN ) wb(interpreter,as.integer(value[i,]))
        else wb(interpreter,value[i,])
      }
    } else if ( is.null(value) ) {
      wb(interpreter,SET)
      wc(interpreter,identifier)
      wb(interpreter,NULLTYPE)
    } else stop("Data structure is not supported.")
    flush(interpreter[['socketIn']])
    echoResponseScala(interpreter,quiet)
  }
  invisible()
}

'$<-.ScalaInterpreter' <- function(interpreter,identifier,value) {
  cc(interpreter)
  if ( substring(identifier,1,1) == "." ) {
    identifier = substring(identifier,2)
    intpSet(interpreter,identifier,intpWrap(interpreter,value))
  } else {
    intpSet(interpreter,identifier,value)
  }
  interpreter
}

intpDef.ScalaInterpreter <- function(interpreter,args,body,interpolate="",quiet="",reference=NULL) {
  cc(interpreter)
  tmpFunc <- NULL
  args <- gsub("^\\s+$","",args)
  body <- paste(body,collapse="\n")
  if ( ( ( interpolate == "" ) && ( get("interpolate",envir=interpreter[['env']]) ) ) || ( interpolate == TRUE ) ) {
    args <- strintrplt(args,parent.frame())
    body <- strintrplt(body,parent.frame())
  }
  wb(interpreter,DEF)
  rscalaReference <- reference
  prependedArgs <- if ( ! is.null(reference) ) paste0("rscalaReference: ",reference[['type']],ifelse(args=="","",","),args)
  else args
  wc(interpreter,prependedArgs)
  wc(interpreter,body)
  flush(interpreter[['socketIn']])
  status <- rb(interpreter,integer(0))
  if ( status == OK ) {
    status <- rb(interpreter,integer(0))
    if ( status == OK ) {
      status <- rb(interpreter,integer(0))
      if ( status == OK ) {
        status <- rb(interpreter,integer(0))
        if ( status == OK ) {
          functionName <- rc(interpreter)
          length <- rb(interpreter,integer(0))
          functionParamNames <- if ( length > 0 ) sapply(1:length,function(x) rc(interpreter))
          else character(0)
          functionParamTypes <- if ( length > 0 ) sapply(1:length,function(x) rc(interpreter))
          else character(0)
          functionReturnType <- rc(interpreter)
          convertedCode <- list()
          if ( length > 0 ) for ( i in 1:length ) {
            convertedCode[[i]] <- convert(functionParamNames[i],functionParamTypes[i])
          }
          convertedCodeStr <- paste("    ",unlist(convertedCode),sep="",collapse="\n")
          argsStr <- if ( ! is.null(reference) ) paste(functionParamNames[-1],collapse=",")
          else paste(functionParamNames,collapse=",")
          if ( nchar(argsStr) > 0 ) argsStr <- paste(argsStr,", ",sep="")
          functionSnippet <- strintrplt('
  tmpFunc <- function(@{argsStr}as.reference=NA,quiet="",gc=FALSE) {
@{convertedCodeStr}
    if ( gc ) intpGC(interpreter)
    rscala:::wb(interpreter,rscala:::INVOKE)
    rscala:::wc(interpreter,"@{functionName}")
    flush(interpreter[["socketIn"]])
    rscala:::rServe(interpreter)
    status <- rscala:::rb(interpreter,integer(0))
    rscala:::echoResponseScala(interpreter,quiet)
    if ( status == rscala:::ERROR ) {
      stop("Invocation error.")
    } else {
      result <- intpGet(interpreter,"?",as.reference=as.reference)
      if ( is.null(result) ) invisible(result)
      else result
    }
  }')
          source(textConnection(functionSnippet),local=TRUE)
        } else {
          echoResponseScala(interpreter,quiet)
          stop("Evaluation error.")
        }
      } else {
        echoResponseScala(interpreter,quiet)
        stop("Evaluation error.")
      }
    } else {
      echoResponseScala(interpreter,quiet)
      stop("Unsupported data type.")
    }
  } else {
    echoResponseScala(interpreter,quiet)
    stop("Error in parsing function arguments.")
  }
  echoResponseScala(interpreter,quiet)
  if ( is.null(tmpFunc) ) return(invisible())
  attr(tmpFunc,"args") <- args
  attr(tmpFunc,"body") <- body
  attr(tmpFunc,"type") <- functionReturnType
  tmpFunc
}

scalap <- function(interpreter,item.name) {
  if ( ! inherits(interpreter,"ScalaInterpreter") ) stop("The first argument must be an interpreter.")
  cc(interpreter)
  wb(interpreter,SCALAP)
  wc(interpreter,item.name)
  flush(interpreter[['socketIn']])
  cat(rc(interpreter),"\n")
}

intpWrap.ScalaInterpreter <- function(interpreter,value) {
  cc(interpreter)
  interpreter[['workspace']]$.rscala.wrap <- value
  interpreter$.R$getR(".rscala.wrap",as.reference=TRUE)
}

intpUnwrap.ScalaInterpreter <- function(interpreter,value) {
  cc(interpreter)
  if ( is.null(value) ) NULL
  else get(value$name(),envir=interpreter[['workspace']]$.)
}

intpGC.ScalaInterpreter <- function(interpreter) {
  cc(interpreter)
  gc()
  markedForGC <- get("markedForGC",interpreter[["env"]])
  if ( length(markedForGC) > 0 ) {
    wb(interpreter,GC)
    wb(interpreter,length(markedForGC))
    wb(interpreter,markedForGC)
    flush(interpreter[['socketIn']])
    assign("markedForGC",integer(0),interpreter[["env"]])
  }
  invisible()
}

intpReset.ScalaInterpreter <- function(interpreter) {
  cc(interpreter)
  wb(interpreter,RESET)
  flush(interpreter[['socketIn']])
}

close.ScalaInterpreter <- function(con,...) {
  cc(con)
  assign("open",FALSE,envir=con[['env']])
  wb(con,EXIT)
  flush(con[['socketIn']])
}

rscalaPackage <- function(pkgname) {
  environmentOfDependingPackage <- parent.env(parent.frame())
  E <- new.env(parent=environmentOfDependingPackage)
  E$initialized <- FALSE
  E$pkgname <- pkgname
  E$jars <- list.files(system.file("java",package=pkgname),pattern=".*.jar",full.names=TRUE,recursive=TRUE)
  assign("E",E,envir=environmentOfDependingPackage)
  invisible()
}

rscalaLoad <- function(classpath=NULL,...) {
  E <- get("E",envir=parent.env(parent.frame()))
  if ( E$initialized ) return(invisible(FALSE))
  if ( is.null(classpath) ) classpath <- E$jars
  else E$jars <- classpath
  E$s <- scalaInterpreter(classpath=classpath,...)
  E$initialized <- TRUE
  invisible(TRUE)
}

rscalaJar <- function(version="") {
  if ( version == "" ) major.version <- ".*"
  else major.version = substr(version,1,4)
  list.files(system.file("java",package="rscala"),pattern=paste("rscala_",major.version,'-.*[0-9]\\.jar',sep=""),full.names=TRUE)
}

javaCmd <- function(java.home=NULL,verbose=FALSE) {
  successMsg <- "SUCCESS: "
  failureMsg <- "FAILURE: "
  if ( verbose ) techniqueMsg <- "'java.home' argument"
  if ( is.null(java.home) ) {
    if ( verbose ) cat(failureMsg,techniqueMsg," is NULL","\n",sep="")
  } else {
    # Attempt 1
    candidate <- normalizePath(file.path(java.home,"bin",sprintf("java%s",ifelse(.Platform$OS=="windows",".exe",""))),mustWork=FALSE)
    if ( verbose ) techniqueMsg <- sprintf("'java.home' (%s) argument",java.home)
    if ( file.exists(candidate) ) { if ( verbose ) cat(successMsg,techniqueMsg,"\n",sep=""); return(candidate) }
    else if ( verbose ) cat(failureMsg,techniqueMsg,"\n",sep="")
  }
  # Attempt 2
  candidate <- normalizePath(Sys.getenv("JAVACMD"),mustWork=FALSE)
  if ( verbose ) techniqueMsg <- sprintf("JAVACMD (%s) environment variable",Sys.getenv("JAVACMD"))
  if ( file.exists(candidate) ) { if ( verbose ) cat(successMsg,techniqueMsg,"\n",sep=""); return(candidate) }
  else if ( verbose ) cat(failureMsg,techniqueMsg,"\n",sep="")
  # Attempt 3
  java.home.tmp <- Sys.getenv("JAVA_HOME")
  if ( verbose ) techniqueMsg <- sprintf("JAVA_HOME (%s) environment variable",java.home.tmp)
  candidate <- if ( java.home.tmp != "" ) {
    normalizePath(file.path(java.home.tmp,"bin",sprintf("java%s",ifelse(.Platform$OS=="windows",".exe",""))),mustWork=FALSE)
  } else ""
  if ( file.exists(candidate) ) { if ( verbose ) cat(successMsg,techniqueMsg,"\n",sep=""); return(candidate) }
  else if ( verbose ) cat(failureMsg,techniqueMsg,"\n",sep="")
  # Attempt 4
  candidate <- normalizePath(Sys.which("java"),mustWork=FALSE)
  if ( verbose ) techniqueMsg <- "'java' in the shell's search path"
  if ( file.exists(candidate) ) { if ( verbose ) cat(successMsg,techniqueMsg,"\n",sep=""); return(candidate) }
  else if ( verbose ) cat(failureMsg,techniqueMsg,"\n",sep="")
  # Attempt 5
  if ( .Platform$OS == "windows" ) {
    if ( verbose ) techniqueMsg <- "querying the Windows registry"
    readRegistryKey <- function(key.name,value.name) {
      v <- suppressWarnings(system2("reg",c("query",shQuote(key.name),"/v",value.name),stdout=TRUE))
      a <- attr(v,"status")
      if ( (!is.null(a)) && ( a != 0 ) ) return(NULL)
      vv <- v[grep(value.name,v)[1]]
      gsub("(^[[:space:]]+|[[:space:]]+$)", "",strsplit(vv,"REG_SZ")[[1]][2])
    }
    java.key <- "HKEY_LOCAL_MACHINE\\Software\\JavaSoft\\Java Runtime Environment"
    java.version <- readRegistryKey(java.key,"CurrentVersion")
    java.home <- readRegistryKey(sprintf("%s\\%s",java.key,java.version),"JavaHome")
    if ( ! is.null(java.home) ) {
      candidate <- normalizePath(file.path(java.home,"bin",sprintf("java%s",ifelse(.Platform$OS=="windows",".exe",""))),mustWork=FALSE)
      if ( file.exists(candidate) ) { if ( verbose ) cat(successMsg,techniqueMsg,"\n",sep=""); return(candidate) }
      else if ( verbose ) cat(failureMsg,techniqueMsg,"\n",sep="")
    } else if ( verbose ) cat(failureMsg,techniqueMsg,"\n",sep="")
  }
  if ( ! verbose ) javaCmd(java.home=java.home,verbose=TRUE)
  else stop("Cannot find Java executable")
}

scalaInfoEngine <- function(scala.command,verbose) {
  scala.command <- normalizePath(scala.command,mustWork=FALSE)
  if ( ! file.exists(scala.command) ) return(NULL)
  if ( verbose ) {
    cat(sprintf("  * ATTEMPT: Found a candidate (%s)\n",scala.command))
    tab <- "    - "
  }
  scala.home <- dirname(dirname(scala.command))
  jars <- list.files(file.path(scala.home,"lib"),"*.jar",full.names=TRUE)
  libraryJar <- jars[grepl("^scala-library",basename(jars))]
  if ( length(libraryJar) == 0 ) {
    if ( verbose ) cat(tab,sprintf("scala-library.jar is not in 'lib' directory of assumed Scala home (%s)\n",scala.home),sep="")
    scala.home <- NULL
    if ( .Platform$OS != "windows" ) {
      if ( verbose ) cat(tab,"Looking for 'scala.home' property in 'scala' script\n",sep="")
      showArguments <- system.file(file.path("bin","showArguments"),package="rscala")
      scala.home <- suppressWarnings(
        tryCatch({
          output <- system2(scala.command,c("-e",shQuote("")),stdout=TRUE,stderr=FALSE,env=paste0("JAVACMD=",shQuote(showArguments)))
          if ( ( ! is.null(attr(output,"status")) ) &&  ( attr(output,"status") != 0 ) ) NULL
          else sub("^-Dscala.home=","",output[grepl("^-Dscala.home=",output)])[1]
        },error=function(e) { NULL } )
      )
    }
    if ( is.null(scala.home) ) {
      if ( verbose ) cat(tab,"Executing snippet to find 'scala.home'\n",sep="")
      scala.home <- suppressWarnings(
        tryCatch(
          system2(scala.command,c("-e",shQuote("println(sys.props(\"scala.home\"))")),stdout=TRUE,stderr=FALSE)
        ,error=function(e) { NULL } )
      )
    }
    if ( ( is.null(scala.home) ) || ( length(scala.home) != 1 ) ) {
      if ( verbose ) cat(tab,"Cannot get 'scala.home' property from 'scala' script\n",sep="")
      return(NULL)
    }
    jars <- list.files(file.path(scala.home,"lib"),"*.jar",full.names=TRUE)
    libraryJar <- jars[grepl("^scala-library",basename(jars))]
    if ( length(libraryJar) == 0 ) {
      if ( verbose ) cat(tab,sprintf("scala-library.jar is not in 'lib' directory of purported Scala home (%s)\n",scala.home),sep="")
      return(NULL)
    }
  }
  libraryJar <- libraryJar[1]
  major.version <- tryCatch({
    fn <- unz(libraryJar,"library.properties")
    lines <- readLines(fn)
    close(fn)
    version <- sub("^version.number=","",lines[grepl("^version.number=",lines)])[1]
    if ( substring(version,4,4) == "." ) substr(version,1,3)
    else substr(version,1,4)
  },error=function(e) { NULL } )
  if ( is.null(major.version) ) {
    if ( verbose ) cat(sprintf("Cannot get Scala version from library jar (%s)\n",libraryJar))
    return(NULL)
  }
  if ( ! ( major.version %in% c("2.10","2.11") ) ) {
    if ( verbose ) cat(sprintf("Unsupported major version (%s) from Scala executable (%s)\n",major.version,scala.command))
    return(NULL)
  }
  list(cmd=scala.command,home=scala.home,version=version,major.version=major.version,jars=jars)
}

scalaInfo <- function(scala.home=NULL,verbose=FALSE) {
  if ( verbose ) cat("\nSearching for a suitable Scala installation.\n")
  tab <- "  * "
  if ( verbose ) {
    successMsg <- "SUCCESS: "
    failureMsg <- "FAILURE: "
  }
  if ( verbose ) techniqueMsg <- "'scala.home' argument"
  if ( is.null(scala.home) ) {
    if ( verbose ) cat(tab,failureMsg,techniqueMsg," is NULL","\n",sep="")
  } else {
    # Attempt 1
    info <- scalaInfoEngine(file.path(scala.home,"bin","scala"),verbose)
    if ( verbose ) techniqueMsg <- sprintf("'scala.home' (%s) argument",scala.home)
    if ( ! is.null(info) ) { if ( verbose ) cat(tab,successMsg,techniqueMsg,"\n\n",sep=""); return(info) }
    else if ( verbose ) cat(tab,failureMsg,techniqueMsg,"\n",sep="")
  }
  # Attempt 2
  scala.home.tmp <- Sys.getenv("SCALA_HOME")
  if ( verbose ) techniqueMsg <- sprintf("SCALA_HOME (%s) environment variable",scala.home.tmp)
  info <- if ( scala.home.tmp != "" ) {
    scalaInfoEngine(file.path(scala.home.tmp,"bin","scala"),verbose)
  } else NULL
  if ( ! is.null(info) ) { if ( verbose ) cat(tab,successMsg,techniqueMsg,"\n\n",sep=""); return(info) }
  else if ( verbose ) cat(tab,failureMsg,techniqueMsg,"\n",sep="")
  # Attempt 3
  info <- scalaInfoEngine(Sys.which("scala"),verbose)
  if ( verbose ) techniqueMsg <- "'scala' in the shell's search path"
  if ( ! is.null(info) ) { if ( verbose ) cat(tab,successMsg,techniqueMsg,"\n\n",sep=""); return(info) }
  else if ( verbose ) cat(tab,failureMsg,techniqueMsg,"\n",sep="")
  # Attempt 4
  installDir <- normalizePath(file.path("~",".rscala",sprintf("scala-%s",CURRENT_SUPPORTED_SCALA_VERSION)),mustWork=FALSE)
  info <- scalaInfoEngine(file.path(installDir,"bin","scala"),verbose)
  if ( verbose ) techniqueMsg <- sprintf("special installation directory (%s)",installDir)
  if ( ! is.null(info) ) { if ( verbose ) cat(tab,successMsg,techniqueMsg,"\n\n",sep=""); return(info) }
  else if ( verbose ) cat(tab,failureMsg,techniqueMsg,"\n",sep="")
  # Attempt 5
  if ( ! verbose ) scalaInfo(scala.home=scala.home,verbose=TRUE)
  else {
    cat("Cannot find a suitable Scala installation.\n\n")
    f <- file(system.file(file.path("doc","README"),package="rscala"),open="r")
    readme <- readLines(f)
    close(f)
    cat(readme,sep="\n")
    cat("\n")
    NULL
  }
}

# Private

evalAndGet <- function(interpreter,snippet,as.reference) {
  tryCatch({
    intpEval(interpreter,snippet,interpolate=FALSE)
    intpGet(interpreter,".",as.reference=as.reference)
  },error=function(e) return(invisible()))
}

checkType <- function(x) {
  if ( is.integer(x) ) INTEGER
  else if ( is.double(x) ) DOUBLE
  else if ( is.logical(x) ) BOOLEAN
  else if ( is.character(x) ) STRING
  else stop("Unsupported data type.")
}

checkType2 <- function(x) {
  if ( is.integer(x) ) "Int"
  else if ( is.double(x) ) "Double"
  else if ( is.logical(x) ) "Boolean"
  else if ( is.character(x) ) "String"
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

echoResponseScala <- function(interpreter,quiet) {
  response <- rc(interpreter)
  if ( ( ( quiet == "" ) && ( ! get("quiet",envir=interpreter[['env']]) ) ) || ( quiet == FALSE ) ) {
    cat(response)
  }
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

scalaInstall <- function() {
  installPath <- normalizePath(file.path("~",".rscala"),mustWork=FALSE)
  url <- sprintf("http://downloads.typesafe.com/scala/%s/scala-%s.tgz",CURRENT_SUPPORTED_SCALA_VERSION,CURRENT_SUPPORTED_SCALA_VERSION)
  dir.create(installPath,showWarnings=FALSE,recursive=TRUE)
  destfile <- file.path(installPath,basename(url))
  result <- download.file(url,destfile)
  if ( result != 0 ) return(invisible(result))
  result <- untar(destfile,exdir=installPath,tar="internal")    # Use internal to avoid problems on a Mac.
  unlink(destfile)
  if ( result == 0 ) cat("Successfully installed Scala in ",file.path(installPath,sprintf("scala-%s",CURRENT_SUPPORTED_SCALA_VERSION)),"\n",sep="")
  invisible(result)
}

