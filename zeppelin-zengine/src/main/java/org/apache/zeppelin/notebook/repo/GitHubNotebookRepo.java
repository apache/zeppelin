package org.apache.zeppelin.notebook.repo;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;

public class GitHubNotebookRepo extends GitNotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(GitNotebookRepo.class);
  private ZeppelinConfiguration zeppelinConfiguration;
  private Git git;

  public GitHubNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    super(conf);

    this.git = super.getGit();
    this.zeppelinConfiguration = conf;

    configureRemoteStream();
    pullFromRemoteStream();
  }

  @Override
  public Revision checkpoint(String pattern, String commitMessage, AuthenticationInfo subject) {
    Revision revision = super.checkpoint(pattern, commitMessage, subject);

    updateRemoteStream();

    return revision;
  }

  private void configureRemoteStream() {
    try {
      LOG.debug("Setting up remote stream");
      RemoteAddCommand remoteAddCommand = git.remoteAdd();
      remoteAddCommand.setName(zeppelinConfiguration.getZeppelinNotebookGitRemoteOrigin());
      remoteAddCommand.setUri(new URIish(zeppelinConfiguration.getZeppelinNotebookGitURL()));
      remoteAddCommand.call();
    } catch (GitAPIException e) {
      e.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }

  private void updateRemoteStream() {
    LOG.debug("Updating remote stream");

    pullFromRemoteStream();
    pushToRemoteSteam();
  }

  private void pullFromRemoteStream() {
    try {
      LOG.debug("Pull latest changed from remote stream");
      PullCommand pullCommand = git.pull();
      pullCommand.setCredentialsProvider(
              new UsernamePasswordCredentialsProvider(
                      zeppelinConfiguration.getZeppelinNotebookGitUsername(),
                      zeppelinConfiguration.getZeppelinNotebookGitAccessToken()
              )
      );

      pullCommand.call();

    } catch (GitAPIException e) {
      LOG.error("Error when pulling latest changes from remote repository");
      e.printStackTrace();
    }
  }

  private void pushToRemoteSteam() {
    try {
      LOG.debug("Push latest changed from remote stream");
      PushCommand pushCommand = git.push();
      pushCommand.setCredentialsProvider(
              new UsernamePasswordCredentialsProvider(
                      zeppelinConfiguration.getZeppelinNotebookGitUsername(),
                      zeppelinConfiguration.getZeppelinNotebookGitAccessToken()
              )
      );

      pushCommand.call();
    } catch (GitAPIException e) {
      LOG.error("Error when pushing latest changes from remote repository");
      e.printStackTrace();
    }
  }
}
