<template>
  <div id="app">
    <Header />

    <Split
      :gutterSize="2"
    >
      <SplitArea :size="25">
          <LeftSidebar />
      </SplitArea>
      <SplitArea :size="75" id="content-pane" class="scrollbar">
        <router-view/>
        <!-- <router-view :key="$route.path"/> -->
      </SplitArea>
    </Split>

    <!-- <StatusBar /> -->

    <GlobalEvents
      @keyup.ctrl.n="executeCommand('note', 'show-create')"
      @keyup.ctrl.f="executeCommand('note', 'find-and-replace')"
      @keyup.ctrl.r="executeCommand('note', 'run-all')"
    />

    <CreateNote />
    <ImportNote />
    <RenameNote />
  </div>
</template>

<script>
import 'bootstrap/dist/css/bootstrap.css'
import GlobalEvents from 'vue-global-events'

import ws from '@/services/ws-helper'

import Header from '@/components/Layout/Header.vue'
import LeftSidebar from '@/components/Layout/LeftSideBar.vue'
// import StatusBar from '@/components/Layout/StatusBar.vue'

import CreateNote from '@/components/Notebook/Create.vue'
import ImportNote from '@/components/Notebook/Import.vue'
import RenameNote from '@/components/Notebook/Rename.vue'

export default {
  name: 'App',
  components: { GlobalEvents, Header, LeftSidebar, CreateNote, ImportNote, RenameNote },
  created () {
    document.title = 'Zeppelin Notebook'
  },
  beforeCreate () {
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

@import '@/assets/loaders/code.scss';
@import '@/assets/loaders/ring.scss';

@import '@/assets/jquery.menu.scss';
@import '@/assets/scrollbar.scss';
@import '@/assets/tabs.scss';
@import '@/assets/status.scss';
@import '@/assets/placeholder.scss';

body {
  color: #2c3e50;

  font-size: 14px;
  line-height: 1.5;

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
    height: calc(100% - 40px);
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
