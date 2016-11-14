strintrplt <- function(snippet,envir=parent.frame()) {
  if ( ! is.character(snippet) ) stop("Character vector expected.")
  if ( length(snippet) != 1 ) stop("Length of vector must be exactly one.")
  m <- regexpr("@\\{([^\\}]+)\\}",snippet)
  if ( m != -1 ) {
    s1 <- substr(snippet,1,m-1)
    s2 <- substr(snippet,m+2,m+attr(m,"match.length")-2)
    s3 <- substr(snippet,m+attr(m,"match.length"),nchar(snippet))
    strintrplt(paste(s1,paste(toString(eval(parse(text=s2),envir=envir)),collapse=" ",sep=""),s3,sep=""),envir)
  } else snippet
}



