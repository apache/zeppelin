namespace java com.nflabs.zeppelin.interpreter.thrift


struct RemoteInterpreterContext {
  1: string paragraphId,
  2: string paragraphTitle,
  3: string paragraphText,
  4: string config,   // json serialized config
  5: string gui       // json serialized gui
}

struct RemoteInterpreterResult {
  1: string code,
  2: string msg
}

service RemoteInterpreterService {
  i32 createInterpreter(1: string className, 2: map<string, string> properties);

  void open(1: i32 intpId);
  void close(1: i32 intpId);


  RemoteInterpreterResult interpret(1: i32 intpId, 2: string st, 3: RemoteInterpreterContext interpreterContext);




}