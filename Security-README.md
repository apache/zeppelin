# Vocabulary
username, owner and principal are used interchangeably to designate the currently authenticated user
# What are we securing ?
Zeppelin is basically a web application that spawn remote interpreters to run commands and return HTML fragments to be displayed on the user browser.
The scope of this PR is to secure the access to the notebooks. A user has access to his notebooks and only his notebooks. To achieve this, we use Apache Shiro. 
## HTTP Endpoint security
Apache Shiro sits as a servlet filter between the browser and the exposed services and handles the required authentication without any programming required. (See Apache Shiro for more info).
## Websocket security
Securing the HTTP endpoints is not enough, since Zeppelin also communicates with the browser through websockets. To secure this channel, we take the following approach:
1. The browser on startup requests a ticket through HTTP
2. The Apache Shiro Servlet filter handles the user auth
3. Once the user is authenticated, a ticket is assigned to this user and the ticket is returned to the browser

All websockets communications require the username and ticket  to be submitted by the browser. Upon receiving a websocket message, the server checks that the ticket received is the one assigned to the username through the HTTP request (step 3 above).

# Strategies to access the principal
Apache Shiro expose the principal through the following call

	org.apache.shiro.SecurityUtils.getSubject().getPrincipal()

Apache Shiro stores the `principal` and the `subject` in a thread local variable.

That makes it possible to get the principal from wherever we need it in the application as long as we are in the context of a HTTP synchronous request.

Two strategies are possible : (1) Rely on the Thread local (anti ?)pattern to get the principal whenever we need it or (2) modify the interfaces of the NotebookRepo and SearchService classes to require the owner to be passed as a parameter.

## Relying on ThreadLocal
The Shiro ThreadLocal subject is available only for HTTP request which is not enough since we need to access notes through websockets (NotebookServer class) where the Shiro filter is not involved. 

We could however explicitly create the Shiro `subject`   on each websocket request using the provided username and ticket in the web socket request. 

The drawback of this approach is that it makes it impossible to write async services and/or multithreaded code involving  access to the Shiro principal (The subject is available in the current thread only).

On the other side, the main benefit of this approach is that it does not require any change to the NotebookRepo and the SearchService interfaces.

## Updating the service interfaces
Coming from an actor based concurrent & distributed programming background I have decided to go for this approach. This required me to add the owner parameter to a couple of method.

	trait NotebookRepo {
	...
	public List<NoteInfo> list(String owner) throws IOException;
	public Note get(String noteId, String owner) throws IOException;
	...
	}
	trait SearchService {
	  public List<~> query(String queryStr, String owner);
	}


As you can guess the has a significant impact on the existing code since the owner parameter had to be made available explicitly along the code that is executed to handle the request.

# How Notes are stored
TODO
# Future : How Permissions could be implemented (note sharing, accessible features in iframes  …)
TODO





\`\`\`
\`
\`\`\`\`