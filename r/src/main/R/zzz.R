typeMap <- list()
typeMap[[INTEGER]] <- integer(0)
typeMap[[DOUBLE]] <- double(0)
typeMap[[BOOLEAN]] <- integer(0)
typeMap[[STRING]] <- character(0)

.onAttach <- function(libname, pkgname) {
  output <- capture.output({
    info <- scalaInfo()
  })
  if ( is.null(info) ) packageStartupMessage(paste(output,collapse="\n"))
}
