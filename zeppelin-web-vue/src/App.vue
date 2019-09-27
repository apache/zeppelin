<template>
  <div id="app">
    <Header />

    <Split
      :gutterSize="2"
    >
      <SplitArea :size="25">
          <LeftSidebar />
          <Preferences />
      </SplitArea>
      <SplitArea :size="75" id="content-pane" class="scrollbar">
        <router-view />
      </SplitArea>
    </Split>

    <StatusBar />

    <GlobalEvents
      @keyup.ctrl.n="executeCommand('note', 'show-create')"
      @keyup.ctrl.f="executeCommand('note', 'find-and-replace')"
      @keyup.ctrl.r="executeCommand('note', 'run-all')"
    />

    <Create />
    <Import />
  </div>
</template>

<script>
import 'bootstrap/dist/css/bootstrap.css'
import GlobalEvents from 'vue-global-events'

import ws from '@/services/ws-helper'

import Header from '@/components/Layout/Header.vue'
import LeftSidebar from '@/components/Layout/LeftSideBar.vue'
import StatusBar from '@/components/Layout/StatusBar.vue'
import Preferences from '@/components/Tools/Preferences.vue'

import Create from '@/components/Notebook/Create.vue'
import Import from '@/components/Notebook/Import.vue'

export default {
  name: 'App',
  components: { GlobalEvents, Header, LeftSidebar, StatusBar, Preferences, Create, Import },
  created () {
    document.title = 'Zeppelin Notebook'
  },
  beforeMount () {
    ws.init(this)

    // get interpreters for note creation
    this.$store.dispatch('getInterpreters')
  },
  computed: {

  },
  mounted () {

  },
  methods: {
    executeCommand (type, command, arg) {
      this.$root.executeCommand(type, command, arg)
    }
  }
}
</script>

<style lang="scss">
@import '@/assets/reset.scss';

@import '@/assets/jquery.menu.scss';
@import '@/assets/scrollbar.scss';
@import '@/assets/tabs.scss';
@import '@/assets/loaders/code.scss';
@import '@/assets/status.scss';
@import '@/assets/placeholder.scss';

body {
  color: #2c3e50;

  a {
    text-decoration: none;
    color: #333;
  }
}

.h100 {
  height: 100%;
}

.w100 {
  width: 100%;
}

#app {
  height: 100%;
  width: 100%;

  > .split {
    height: calc(100% - 67px - 24px);
    border-top: 1px solid #F1F1F1;
  }

  a {
    text-decoration: none;
  }
}

.split {
  .gutter {
    &.gutter-horizontal {
      cursor: ew-resize;
      background-image: none;
    }

    &:hover {
      background-color: #049EE0;
    }
  }
}

#content-pane {
  background-color: #fcfcfc;
  padding: 8px;
}
</style>
