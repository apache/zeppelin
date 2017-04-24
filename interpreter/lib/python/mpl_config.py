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

# This module provides utitlites for users to configure the inline plotting
# backend through a PyZeppelinContext instance (eg, through z.configure_mpl())

import matplotlib

def configure(**kwargs):
    """
    Generic configure function.
    Usage: configure(prop1='foo', prop2='bar', ...)
    Currently supported zeppelin-specific properties are:
        interactive - If true show all figures without explicit call to show()
                      via a post-execute hook.
        angular - If true, bind figures to angular display system.
        close - If true, close all figures once shown.
        width, height - Default width / height of the figure in pixels.
        fontsize - Font size.
        dpi - dpi of the figure.
        fmt - Figure format
        supported_formats - Supported Figure formats ()
        context - ZeppelinContext instance (requires PY4J)
    """
    _config.update(**kwargs)
        
    # Broadcast relevant changes to matplotlib RC
    _on_config_change()


def get(key):
    """
    Get the configuration info given a key
    """
    return _config[key]


def _on_config_change():
    # dpi
    dpi = _config['dpi']
    
    # For older versions of matplotlib, savefig.dpi is not synced with
    # figure.dpi by default
    matplotlib.rcParams['figure.dpi'] = dpi
    if matplotlib.__version__ < '2.0.0':
        matplotlib.rcParams['savefig.dpi'] = dpi
    
    # Width and height
    width = float(_config['width']) / dpi
    height = float(_config['height']) / dpi
    matplotlib.rcParams['figure.figsize'] = (width, height)
    
    # Font size
    fontsize = _config['fontsize']
    matplotlib.rcParams['font.size'] = fontsize
    
    # Default Figure Format
    fmt = _config['format']
    supported_formats = _config['supported_formats']
    if fmt not in supported_formats:
        raise ValueError("Unsupported format %s" %fmt)
    matplotlib.rcParams['savefig.format'] = fmt
    
    # Interactive mode
    interactive = _config['interactive']
    matplotlib.interactive(interactive)
    
    
def _init_config():
    dpi = matplotlib.rcParams['figure.dpi']
    fmt = matplotlib.rcParams['savefig.format']
    width, height = matplotlib.rcParams['figure.figsize']
    fontsize = matplotlib.rcParams['font.size']
    _config['dpi'] = dpi
    _config['format'] = fmt
    _config['width'] = width*dpi
    _config['height'] = height*dpi
    _config['fontsize'] = fontsize
    _config['close'] = True
    _config['interactive'] = matplotlib.is_interactive()
    _config['angular'] = False
    _config['supported_formats'] = ['png', 'jpg', 'svg']
    _config['context'] = None


_config = {}
_init_config()
