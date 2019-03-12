/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <dirent.h>
#include <fcntl.h>
#include <fts.h>
#include <errno.h>
#include <grp.h>
#include <unistd.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <sys/stat.h>
#include <sys/mount.h>
#include <sys/types.h>
#include <pwd.h>

FILE *LOGFILE = NULL;
FILE *ERRORFILE = NULL;
int SETUID_OPER_FAILED = 10;
int USER_NOT_FOUND = 20;
int INVALID_INPUT = 30;

/*
 *  Change the real and effective user and group from super user to the specified user
 *
 *  Adopted from:
 *  ./hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-nodemanager/src/main/native/container-executor/impl/container-executor.c
 *
 */

int change_user(char *username, uid_t user, gid_t group) {
    if (user == getuid() && user == geteuid() &&
            group == getgid() && group == getegid()) {
        return 0;
    }

    if (initgroups(username, group) != 0) {
        fprintf(LOGFILE, "Error setting supplementary groups for user %s: %s\n",
            username, strerror(errno));
        return SETUID_OPER_FAILED;
    }
    if (seteuid(0) != 0) {
        fprintf(LOGFILE, "unable to reacquire root - %s\n", strerror(errno));
        fprintf(LOGFILE, "Real: %d:%d; Effective: %d:%d\n",
                getuid(), getgid(), geteuid(), getegid());
        return SETUID_OPER_FAILED;
    }
    if (setgid(group) != 0) {
        fprintf(LOGFILE, "unable to set group to %d - %s\n", group,
                strerror(errno));
        fprintf(LOGFILE, "Real: %d:%d; Effective: %d:%d\n",
                getuid(), getgid(), geteuid(), getegid());
        return SETUID_OPER_FAILED;
    }
    if (setuid(user) != 0) {
        fprintf(LOGFILE, "unable to set user to %d - %s\n", user, strerror(errno));
        fprintf(LOGFILE, "Real: %d:%d; Effective: %d:%d\n",
                getuid(), getgid(), geteuid(), getegid());
        return SETUID_OPER_FAILED;
    }

    return 0;
}

int main(int argc, char **argv){

    // set up the logging stream
    if (!LOGFILE){
        LOGFILE=stdout;
    }
    if (!ERRORFILE){
        ERRORFILE=stderr;
    }

    if (argc < 3) {
        fprintf(ERRORFILE, "Requires at least 3 variables: ./execute-as-user username command [args]");
        return INVALID_INPUT;
    }

    char *username = argv[1];

    // gather information about user
    struct passwd *user_info = getpwnam(username);
    if (user_info == NULL){
        fprintf(LOGFILE, "user does not exist: %s", username);
        return USER_NOT_FOUND;
    }

    // try to change user
    fprintf(LOGFILE, "Changing user: user: %s, uid: %d, gid: %d\n", username, user_info->pw_uid, user_info->pw_gid);
    int retval = change_user(username, user_info->pw_uid, user_info->pw_gid);
    if (retval != 0){
        fprintf(LOGFILE, "Error changing user to %s\n", username);
        return SETUID_OPER_FAILED;
    }

    // execute the command
    char **user_argv = &argv[2];
    fprintf(LOGFILE, "user command starting from: %s\n", user_argv[0]);
    fflush(LOGFILE);
    retval = execvp(*user_argv, user_argv);
    fprintf(LOGFILE, "system call return value: %d\n", retval);

    // sometimes system(cmd) returns 256, which is interpreted to 0, making a failed job a successful job
    // hence this goofy piece of if statement.
    if (retval != 0){
        return 1;
    }
    else{
        return 0;
    }

}