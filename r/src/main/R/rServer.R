rServe <- function(sockets) {
  cc(sockets)
  workspace <- sockets[['workspace']]
  debug <- get("debug",envir=sockets[['env']])
  while ( TRUE ) {
    if ( debug ) cat("R DEBUG: Top of the loop waiting for a command.\n")
    cmd <- rb(sockets,integer(0))
    if ( cmd == EXIT ) {
      if ( debug ) cat("R DEBUG: Got EXIT\n")
      return()
    } else if ( cmd == DEBUG ) {
      if ( debug ) cat("R DEBUG: Got DEBUG\n")
      newDebug <- ( rb(sockets,integer(0)) != 0 )
      if ( debug != newDebug ) cat("R DEBUG: Debugging is now ",newDebug,"\n",sep="")
      debug <- newDebug
      assign("debug",debug,envir=sockets[['env']])
    } else if ( cmd == EVAL ) {
      if ( debug ) cat("R DEBUG: Got EVAL\n")
      snippet <- rc(sockets)
      output <- capture.output(result <- try(eval(parse(text=snippet),envir=workspace)))
      if ( inherits(result,"try-error") ) {
        wb(sockets,ERROR)
        msg <- paste(c(output,attr(result,"condition")$message),collapse="\n")
        wc(sockets,msg)
      } else {
        wb(sockets,OK)
        output <- paste(output,collapse="\n")
        wc(sockets,output)
      }
      assign(".rscala.last.value",result,envir=workspace)
    } else if ( cmd %in% c(SET,SET_SINGLE,SET_DOUBLE) ) {
      if ( debug ) cat("R DEBUG: Got SET\n")
      if ( cmd != SET ) index <- rc(sockets)
      identifier <- rc(sockets)
      dataStructure <- rb(sockets,integer(0))
      if ( dataStructure == NULLTYPE ) {
        if ( cmd == SET ) assign(identifier,NULL,envir=workspace)
        else subassign(sockets,identifier,index,NULL,cmd==SET_SINGLE)
      } else if ( dataStructure == ATOMIC ) {
        dataType <- rb(sockets,integer(0))
        if ( dataType == INTEGER ) value <- rb(sockets,integer(0))
        else if ( dataType == DOUBLE ) value <- rb(sockets,double(0))
        else if ( dataType == BOOLEAN ) value <- rb(sockets,integer(0)) != 0
        else if ( dataType == STRING ) value <- rc(sockets)
        else stop(paste("Unknown data type:",dataType))
        if ( cmd == SET ) assign(identifier,value,envir=workspace)
        else subassign(sockets,identifier,index,value,cmd==SET_SINGLE)
      } else if ( dataStructure == VECTOR ) {
        dataLength <- rb(sockets,integer(0))
        dataType <- rb(sockets,integer(0))
        if ( dataType == INTEGER ) value <- rb(sockets,integer(0),n=dataLength)
        else if ( dataType == DOUBLE ) value <- rb(sockets,double(0),n=dataLength)
        else if ( dataType == BOOLEAN ) value <- rb(sockets,integer(0),n=dataLength) != 0
        else if ( dataType == STRING ) value <- sapply(1:dataLength,function(i) rc(sockets))
        else stop(paste("Unknown data type:",dataType))
        if ( cmd == SET ) assign(identifier,value,envir=workspace)
        else subassign(sockets,identifier,index,value,cmd==SET_SINGLE)
      } else if ( dataStructure == MATRIX ) {
        dataNRow <- rb(sockets,integer(0))
        dataNCol <- rb(sockets,integer(0))
        dataLength <- dataNRow * dataNCol
        dataType <- rb(sockets,integer(0))
        if ( dataType == INTEGER ) value <- matrix(rb(sockets,integer(0),n=dataLength),nrow=dataNRow,byrow=TRUE)
        else if ( dataType == DOUBLE ) value <- matrix(rb(sockets,double(0),n=dataLength),nrow=dataNRow,byrow=TRUE)
        else if ( dataType == BOOLEAN ) value <- matrix(rb(sockets,integer(0),n=dataLength),nrow=dataNRow,byrow=TRUE) != 0
        else if ( dataType == STRING ) value <- matrix(sapply(1:dataLength,function(i) rc(sockets)),nrow=dataNRow,byrow=TRUE)
        else stop(paste("Unknown data type:",dataType))
        if ( cmd == SET ) assign(identifier,value,envir=workspace)
        else subassign(sockets,identifier,index,value,cmd==SET_SINGLE)
      } else if ( dataStructure == REFERENCE ) {
        otherIdentifier <- rc(sockets)
        if ( exists(otherIdentifier,envir=workspace$.) ) {
          wb(sockets,OK)
          value <- get(otherIdentifier,envir=workspace$.)
          if ( cmd == SET ) assign(identifier,value,envir=workspace)
          else subassign(sockets,identifier,index,value,cmd==SET_SINGLE)
        } else {
          wb(sockets,UNDEFINED_IDENTIFIER)
        }
      } else stop(paste("Unknown data structure:",dataStructure))
    } else if ( cmd == GET ) {
      if ( debug ) cat("R DEBUG: Got GET\n")
      identifier <- rc(sockets)
      value <- tryCatch(get(identifier,envir=workspace),error=function(e) e)
      if ( is.null(value) ) {
        wb(sockets,NULLTYPE)
      } else if ( inherits(value,"error") ) {
        wb(sockets,UNDEFINED_IDENTIFIER)
      } else if ( ! is.atomic(value) ) {
        wb(sockets,UNSUPPORTED_STRUCTURE)
      } else if ( is.vector(value) ) {
        type <- checkType(value)
        if ( ( length(value) == 1 ) && ( ! get("length.one.as.vector",envir=sockets[['env']]) ) ) {
          wb(sockets,ATOMIC)
        } else {
          wb(sockets,VECTOR)
          wb(sockets,length(value))
        }
        wb(sockets,type)
        if ( type == STRING ) {
          if ( length(value) > 0 ) for ( i in 1:length(value) ) wc(sockets,value[i])
        } else {
          if ( type == BOOLEAN ) wb(sockets,as.integer(value))
          else wb(sockets,value)
        }
      } else if ( is.matrix(value) ) {
        type <- checkType(value)
        wb(sockets,MATRIX)
        wb(sockets,dim(value))
        wb(sockets,type)
        if ( nrow(value) > 0 ) for ( i in 1:nrow(value) ) {
          if ( type == STRING ) {
            if ( ncol(value) > 0 ) for ( j in 1:ncol(value) ) wc(sockets,value[i,j])
          }
          else if ( type == BOOLEAN ) wb(sockets,as.integer(value[i,]))
          else wb(sockets,value[i,])
        }
      } else {
        wb(sockets,UNSUPPORTED_STRUCTURE)
      }
    } else if ( cmd == GET_REFERENCE ) {
      if ( debug ) cat("R DEBUG: Got GET_REFERENCE\n")
      identifier <- rc(sockets)
      value <- tryCatch(get(identifier,envir=workspace),error=function(e) e)
      if ( inherits(value,"error") ) {
        wb(sockets,UNDEFINED_IDENTIFIER)
      } else {
        wb(sockets,REFERENCE)
        wc(sockets,new.reference(value,workspace$.))
      }
    } else if ( cmd == GC ) {
      if ( debug ) cat("R DEBUG: Got GC\n")
      workspace$. <- new.env(parent=workspace)
    } else stop(paste("Unknown command:",cmd))
    flush(sockets[['socketIn']])
  }
}

subassign <- function(sockets,x,i,value,single=TRUE) {
  workspace <- sockets[['workspace']]
  assign(".rscala.set.value",value,envir=workspace)
  brackets <- if ( single ) c("[","]") else c("[[","]]")
  output <- capture.output(result <- try(eval(parse(text=paste0(x,brackets[1],i,brackets[2]," <- .rscala.set.value")),envir=workspace)))
  if ( inherits(result,"try-error") ) {
    wb(sockets,ERROR)
    output <- paste(paste(output,collapse="\n"),paste(attr(result,"condition")$message,collapse="\n"),sep="\n")
    wc(sockets,output)
  } else {
    wb(sockets,OK)
  }
  rm(".rscala.set.value",envir=workspace)
  invisible(value)
}

new.reference <- function(value,envir) {
  name <- ""
  while ( ( name == "" ) || ( exists(name,envir=envir) ) ) {  
    name <- paste0(sample(lEtTeRs,1),paste0(sample(alphabet,7,replace=TRUE),collapse=""))
  }
  assign(name,value,envir=envir)
  name
}

