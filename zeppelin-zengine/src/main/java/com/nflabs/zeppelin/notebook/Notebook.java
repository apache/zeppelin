package com.nflabs.zeppelin.notebook;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consist of Notes 
 *
 */
public class Notebook {
	Logger logger = LoggerFactory.getLogger(Notebook.class);

	private SchedulerFactory schedulerFactory;
	private InterpreterFactory replFactory;
	/** Keep the order */
	Map<String, Note> notes = new LinkedHashMap<String, Note>();

	private ZeppelinConfiguration conf;

	public Notebook(ZeppelinConfiguration conf, SchedulerFactory schedulerFactory, InterpreterFactory replFactory) throws IOException {
		this.conf = conf;
		this.schedulerFactory = schedulerFactory;
		this.replFactory = replFactory;
		loadAllNotes();
	}
	
	private boolean isLoaderStatic(){
		return "share".equals(conf.getString(ConfVars.ZEPPELIN_INTERPRETER_MODE));
	}
	
	/**
	 * Create new note
	 * @return
	 */
	public Note createNote() {
		Note note = new Note(conf, new NoteInterpreterLoader(replFactory, isLoaderStatic()));
		synchronized(notes){
			notes.put(note.id(), note);
		}
		return note;
	}

	
	public Note getNote(String id) {
		synchronized(notes){
			return notes.get(id);
		}
	}
	
	public void removeNote(String id) {
		Note note;
		synchronized(notes){
			note = notes.remove(id);
		}
		note.getNoteReplLoader().destroyAll();
		try {
			note.unpersist();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    private void loadAllNotes() throws IOException {
        File notebookDir = new File(conf.getNotebookDir());
        File[] dirs = notebookDir.listFiles();
        if (dirs == null) return;
        for (File f : dirs) {
            boolean isHidden = !f.getName().startsWith(".");
            if (f.isDirectory() && isHidden) {
                Scheduler scheduler = schedulerFactory.createOrGetFIFOScheduler("note_" + System.currentTimeMillis());
                logger.info("Loading note from " + f.getName());
                Note note = Note.load(f.getName(), conf, new NoteInterpreterLoader(replFactory, isLoaderStatic()), scheduler);
                synchronized (notes) {
                    notes.put(note.id(), note);
                }
            }
        }
    }

    public List<Note> getAllNotes() {
		synchronized(notes){
            List<Note> noteList = new ArrayList<Note>(notes.values());
            logger.info(""+noteList.size());
            Collections.sort(noteList, new Comparator() {
                @Override
                public int compare(Object one, Object two) {
                    Note note1 = (Note) one;
                    Note note2 = (Note) two;

                    String name1 = note1.id();
                    if (note1.getName() != null) {
                        name1 = note1.getName();
                    }
                    String name2 = note2.id();
                    if (note2.getName() != null) {
                        name2 = note2.getName();
                    }
                    ((Note) one).getName();
                    return name1.compareTo(name2);
                }
            });
            return noteList;
		}
	}
}
