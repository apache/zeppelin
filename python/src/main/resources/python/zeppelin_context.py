#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os, sys
import warnings
import base64
import threading

from io import BytesIO

try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO


class PyZeppelinContext(object):
    """ A context impl that uses Py4j to communicate to JVM
    """

    def __init__(self, z, gateway):
        self.z = z
        self.gateway = gateway
        self.paramOption = gateway.jvm.org.apache.zeppelin.display.ui.OptionInput.ParamOption
        self.javaList = gateway.jvm.java.util.ArrayList
        self.max_result = z.getMaxResult()
        self._displayhook = lambda *args: None
        self._setup_matplotlib()
        self._apiHandlers = {}
        t = threading.Thread(target=self._handleApiRequestThread)
        t.start()

    # By implementing special methods it makes operating on it more Pythonic
    def __setitem__(self, key, item):
        self.z.put(key, item)

    def __getitem__(self, key):
        return self.z.get(key)

    def __delitem__(self, key):
        self.z.remove(key)

    def __contains__(self, item):
        return self.z.containsKey(item)

    def add(self, key, value):
        self.__setitem__(key, value)

    def put(self, key, value):
        self.__setitem__(key, value)

    def get(self, key):
        return self.__getitem__(key)

    def getInterpreterContext(self):
        return self.z.getInterpreterContext()

    def input(self, name, defaultValue=""):
        return self.z.input(name, defaultValue)

    def textbox(self, name, defaultValue=""):
        return self.z.textbox(name, defaultValue)

    def password(self, name):
        return self.z.password(name)

    def noteTextbox(self, name, defaultValue=""):
        return self.z.noteTextbox(name, defaultValue)

    def select(self, name, options, defaultValue=""):
        return self.z.select(name, defaultValue, self.getParamOptions(options))

    def noteSelect(self, name, options, defaultValue=""):
        return self.z.noteSelect(name, defaultValue, self.getParamOptions(options))

    def checkbox(self, name, options, defaultChecked=[]):
        return self.z.checkbox(name, self.getDefaultChecked(defaultChecked), self.getParamOptions(options))

    def noteCheckbox(self, name, options, defaultChecked=[]):
        return self.z.noteCheckbox(name, self.getDefaultChecked(defaultChecked), self.getParamOptions(options))

    def addRestApi(self, name, fn):
        self._apiHandlers[name] = fn
        return self.z.addRestApiHandler(name)

    def _handleApiRequestThread(self):
        while True:
          msg = self.z.getNextApiRequestFromQueue()
          if msg == None:
              continue

          endpoint = msg.getEndpoint()
          request = msg.getRequestBody()
          fn = self._apiHandlers[endpoint]

          try:
              ret = fn(request)
              if isinstance(ret, tuple):
                msg.setResponseHeader(ret[1])
                msg.setResponseBody(ret[0])
              else:
                msg.setResponseBody(ret)
          except:
              err = sys.exc_info()[0]
              msg.setResponseBody(str(err))

    def registerHook(self, event, cmd, replName=None):
        if replName is None:
            self.z.registerHook(event, cmd)
        else:
            self.z.registerHook(event, cmd, replName)

    def unregisterHook(self, event, replName=None):
        if replName is None:
            self.z.unregisterHook(event)
        else:
            self.z.unregisterHook(event, replName)

    def registerNoteHook(self, event, cmd, noteId, replName=None):
        if replName is None:
            self.z.registerNoteHook(event, cmd, noteId)
        else:
            self.z.registerNoteHook(event, cmd, noteId, replName)

    def unregisterNoteHook(self, event, noteId, replName=None):
        if replName is None:
            self.z.unregisterNoteHook(event, noteId)
        else:
            self.z.unregisterNoteHook(event, noteId, replName)

    def getParamOptions(self, options):
        javaOptions = self.gateway.new_array(self.paramOption, len(options))
        i = 0
        for tuple in options:
            javaOptions[i] = self.paramOption(tuple[0], tuple[1])
            i += 1
        return javaOptions

    def getDefaultChecked(self, defaultChecked):
        javaDefaultChecked = self.javaList()
        for check in defaultChecked:
            javaDefaultChecked.append(check)
        return javaDefaultChecked

    def show(self, p, **kwargs):
        if hasattr(p, '__name__') and p.__name__ == "matplotlib.pyplot":
            self.show_matplotlib(p, **kwargs)
        elif type(p).__name__ == "DataFrame": # does not play well with sub-classes
            # `isinstance(p, DataFrame)` would req `import pandas.core.frame.DataFrame`
            # and so a dependency on pandas
            self.show_dataframe(p, **kwargs)
        else:
            print(str(p))
            
    def show_dataframe(self, df, show_index=False, **kwargs):
        """Pretty prints DF using Table Display System
        """
        exceed_limit = len(df) > self.max_result
        header_buf = StringIO("")
        if show_index:
            idx_name = str(df.index.name) if df.index.name is not None else ""
            header_buf.write(idx_name + "\t")
        header_buf.write(str(df.columns[0]))
        for col in df.columns[1:]:
            header_buf.write("\t")
            header_buf.write(str(col))
        header_buf.write("\n")

        body_buf = StringIO("")
        rows = df.head(self.max_result).values if exceed_limit else df.values
        index = df.index.values
        for idx, row in zip(index, rows):
            if show_index:
                body_buf.write("%html <strong>{}</strong>".format(idx))
                body_buf.write("\t")
            body_buf.write(str(row[0]))
            for cell in row[1:]:
                body_buf.write("\t")
                body_buf.write(str(cell))
            body_buf.write("\n")
        body_buf.seek(0)
        header_buf.seek(0)
        print("%table " + header_buf.read() + body_buf.read())
        body_buf.close(); header_buf.close()
        if exceed_limit:
            print("%html <font color=red>Results are limited by {}.</font>".format(self.max_result))

    def show_matplotlib(self, p, fmt="png", width="auto", height="auto",
                        **kwargs):
        """Matplotlib show function
        """
        if fmt == "png":
            img = BytesIO()
            p.savefig(img, format=fmt)
            img_str = b"data:image/png;base64,"
            img_str += base64.b64encode(img.getvalue().strip())
            img_tag = "<img src={img} style='width={width};height:{height}'>"
            # Decoding is necessary for Python 3 compatibility
            img_str = img_str.decode("ascii")
            img_str = img_tag.format(img=img_str, width=width, height=height)
        elif fmt == "svg":
            img = StringIO()
            p.savefig(img, format=fmt)
            img_str = img.getvalue()
        else:
            raise ValueError("fmt must be 'png' or 'svg'")

        html = "%html <div style='width:{width};height:{height}'>{img}<div>"
        print(html.format(width=width, height=height, img=img_str))
        img.close()

    def configure_mpl(self, **kwargs):
        import mpl_config
        mpl_config.configure(**kwargs)

    def _setup_matplotlib(self):
        # If we don't have matplotlib installed don't bother continuing
        try:
            import matplotlib
        except ImportError:
            return

        # Make sure custom backends are available in the PYTHONPATH
        rootdir = os.environ.get('ZEPPELIN_HOME', os.getcwd())
        mpl_path = os.path.join(rootdir, 'interpreter', 'lib', 'python')
        if mpl_path not in sys.path:
            sys.path.append(mpl_path)

        # Finally check if backend exists, and if so configure as appropriate
        try:
            matplotlib.use('module://backend_zinline')
            import backend_zinline

            # Everything looks good so make config assuming that we are using
            # an inline backend
            self._displayhook = backend_zinline.displayhook
            self.configure_mpl(width=600, height=400, dpi=72, fontsize=10,
                               interactive=True, format='png', context=self.z)
        except ImportError:
            # Fall back to Agg if no custom backend installed
            matplotlib.use('Agg')
            warnings.warn("Unable to load inline matplotlib backend, "
                          "falling back to Agg")
