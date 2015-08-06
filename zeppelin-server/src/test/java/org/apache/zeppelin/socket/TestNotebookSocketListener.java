package org.apache.zeppelin.socket;

/**
 * Created by joelz on 8/6/15.
 * This enables mocking a socket listener.
 */
public class TestNotebookSocketListener implements NotebookSocketListener {
    @Override
    public void onClose(NotebookSocket socket, int code, String message) {

    }

    @Override
    public void onOpen(NotebookSocket socket) {

    }

    @Override
    public void onMessage(NotebookSocket socket, String message) {

    }
}
