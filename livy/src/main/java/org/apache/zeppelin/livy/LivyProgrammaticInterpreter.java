package org.apache.zeppelin.livy;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Livy Interpreter for shared kind which share SparkContext across spark/pyspark/r.
 */
public class LivyProgrammaticInterpreter extends BaseLivyInterpreter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivySharedInterpreter.class);

    private boolean isSupported = false;

    public LivyProgrammaticInterpreter(Properties property) {
        super(property);
    }

    @Override
    public void open() throws InterpreterException {
        try {
            // check livy version
            try {
                this.livyVersion = getLivyVersion();
                LOGGER.info("Use livy " + livyVersion);
                System.out.println(livyVersion);
            } catch (APINotFoundException e) {
                // assume it is livy 0.2.0 when livy doesn't support rest api of fetching version.
                this.livyVersion = new LivyVersion("0.2.0");
                LOGGER.info("Use livy 0.2.0");
            }

            if (livyVersion.isProgrammaticSupported()) {
                LOGGER.info("LivySharedInterpreter is supported.");
                isSupported = true;
                initLivySession();
            } else {
                LOGGER.info("LivySharedInterpreter is not supported.");
                isSupported = false;
            }
        } catch (LivyException e) {
            String msg = "Fail to create session, please check livy interpreter log and " +
                    "livy server log";
            throw new InterpreterException(msg, e);
        }
    }

    public boolean isSupported() {
        return isSupported;
    }

    public InterpreterResult interpret(String st, String codeType, InterpreterContext context) {
        if (StringUtils.isEmpty(st)) {
            return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
        }

        try {
            return interpret(st, codeType, context.getParagraphId(), this.displayAppInfo, true, true);
        } catch (LivyException e) {
            LOGGER.error("Fail to interpret:" + st, e);
            return new InterpreterResult(InterpreterResult.Code.ERROR,
                    InterpreterUtils.getMostRelevantMessage(e));
        }
    }

    @Override
    public String getSessionKind() {
        return "shared";
    }

    @Override
    protected String extractAppId() throws LivyException {
        return null;
    }

    @Override
    protected String extractWebUIAddress() throws LivyException {
        return null;
    }

    public static void main(String[] args) {
        ExecuteRequest request = new ExecuteRequest("1+1", null);
        System.out.println(request.toJson());
    }
}
