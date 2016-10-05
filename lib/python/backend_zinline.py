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

# This file provides a static (non-interactive) matplotlib plotting backend
# for zeppelin notebooks for use with the python/pyspark interpreters

from __future__ import print_function

import base64
from io import BytesIO
try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO

import mpl_config
import matplotlib
from matplotlib._pylab_helpers import Gcf
from matplotlib.backends.backend_agg import new_figure_manager, FigureCanvasAgg
from matplotlib.backend_bases import ShowBase, FigureManagerBase
from matplotlib.figure import Figure

########################################################################
#
# The following functions and classes are for pylab and implement
# window/figure managers, etc...
#
########################################################################

class Show(ShowBase):
    '''
    A callable object that displays the figures to the screen. Valid kwargs
    include figure width and height (in units supported by the div tag), block
    (allows users to override blocking behavior regardless of whether or not
    interactive mode is enabled, currently unused) and close (Implicitly call
    matplotlib.pyplot.close('all') with each call to show()).
    '''
    def __call__(self, close=None, block=None, **kwargs):
        if close is None:
            close = mpl_config.get('close')
        try:
            managers = Gcf.get_all_fig_managers()
            if not managers:
                return

            # Tell zeppelin that the output will be html using the %html magic
            # We want to do this only once to avoid seeing "%html" printed
            # directly to the outout when multiple figures are displayed from
            # one paragraph.
            print("%html")

            # Show all open figures
            for manager in managers:
                manager.show(**kwargs)
        finally:
            # This closes all the figures if close is set to True.
            if close and Gcf.get_all_fig_managers():
                Gcf.destroy_all()


class FigureCanvasZInline(FigureCanvasAgg):
    """
    The canvas the figure renders into. Calls the draw and print fig
    methods, creates the renderers, etc...
    """
    def draw_idle(self, *args, **kwargs):
        """
        Called when the figure gets updated (eg through a plotting command).
        This is overriden to allow open figures to be reshown after they
        are updated when mpl_config.get('close') is False.
        """
        if not self._is_idle_drawing:
            with self._idle_draw_cntx():
                self.draw(*args, **kwargs)
                draw_if_interactive()
                

class FigureManagerZInline(FigureManagerBase):
    """
    Wrap everything up into a window for the pylab interface
    """
    def __init__(self, canvas, num):
        FigureManagerBase.__init__(self, canvas, num)
        self._shown = False

    def show(self, **kwargs):
        if not self._shown:
            zdisplay(self.canvas.figure, **kwargs)
        else:
            self.canvas.draw_idle()
            
        self._shown = True


def draw_if_interactive():
    """
    If interactive mode is on, this allows for updating properties of
    the figure when each new plotting command is called.
    """
    manager = Gcf.get_active()
    
    # Don't bother continuing if we aren't in interactive mode
    # or if there are no active figures
    if not matplotlib.is_interactive() or manager is None:
        return
        
    # Allow for figure to be reshown if close is false since
    # this function call implies that it has been updated
    if not mpl_config.get('close'):
        manager._shown = False
        

def new_figure_manager(num, *args, **kwargs):
    """
    Create a new figure manager instance
    """
    # if a main-level app must be created, this (and
    # new_figure_manager_given_figure) is the usual place to
    # do it -- see backend_wx, backend_wxagg and backend_tkagg for
    # examples.  Not all GUIs require explicit instantiation of a
    # main-level app (egg backend_gtk, backend_gtkagg) for pylab
    FigureClass = kwargs.pop('FigureClass', Figure)
    thisFig = FigureClass(*args, **kwargs)
    return new_figure_manager_given_figure(num, thisFig)


def new_figure_manager_given_figure(num, figure):
    """
    Create a new figure manager instance for the given figure.
    """
    canvas = FigureCanvasZInline(figure)
    manager = FigureManagerZInline(canvas, num)
    return manager


########################################################################
#
# Backend specific functions
#
########################################################################
            
def zdisplay(fig, **kwargs):
    """
    Publishes a matplotlib figure to the notebook paragraph output.
    """
    # kwargs can be width or height (in units supported by div tag)
    width = kwargs.pop('width', 'auto')
    height = kwargs.pop('height', 'auto')
    fmt = kwargs.get('format', mpl_config.get('format'))

    # Check if format is supported
    supported_formats = mpl_config.get('supported_formats')
    if fmt not in supported_formats:
        raise ValueError("Unsupported format %s" %fmt)
    
    # For SVG the data string has to be unicode, not bytes
    if fmt == 'svg':
        buf = StringIO()
        fig.canvas.print_figure(buf, **kwargs)
        img_str = buf.getvalue()
        
        # This is needed to ensure the SVG image is the correct size.
        # We should find a better way to do this...
        width = '{}px'.format(mpl_config.get('width'))
        height = '{}px'.format(mpl_config.get('height'))
    else:
        # Express the image as bytes
        buf = BytesIO()
        fig.canvas.print_figure(buf, **kwargs)
        img_str = b"data:image/%s;base64," %fmt
        img_str += base64.b64encode(buf.getvalue())
        img_tag = "<img src={img} style='width={width};height:{height}'>"
    
        # Python3 forces all strings to default to unicode, but for raster image
        # formats (eg png, jpg), we want to work with bytes. Thus this step is
        # needed to ensure compatability for all python versions.
        img_str = img_str.decode("ascii")
        img_str = img_tag.format(img=img_str, width=width, height=height)
    
    # Print the image to the notebook paragraph via the %html magic
    html = "<div style='width:{width};height:{height}'>{img}<div>"
    print(html.format(width=width, height=height, img=img_str))
    buf.close()

def displayhook():
    """
    Called post paragraph execution if interactive mode is on
    """
    if matplotlib.is_interactive():
        show()

########################################################################
#
# Now just provide the standard names that backend.__init__ is expecting
#
########################################################################

# Create a reference to the show function we are using. This is what actually
# gets called by matplotlib.pyplot.show().
show = Show()

# Default FigureCanvas and FigureManager classes to use from the backend
FigureCanvas = FigureCanvasZInline
FigureManager = FigureManagerZInline
