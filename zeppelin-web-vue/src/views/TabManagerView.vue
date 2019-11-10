<template>
  <div class="h100 w100">
    <TabManager
      v-if="this.tabList.length > 0"
      class="tabs-manager"
    >
      <Tab
        v-for="currentTab in this.tabList"
        :key="currentTab.id"
        :id="currentTab.id"
        :name="currentTab.name"
        :tabContent="currentTab"
      >
        <Note v-if="currentTab.type == 'note'" :note="currentTab" />
        <Interpreter v-if="currentTab.type == 'interpreters'" />
        <Configurations v-if="currentTab.type == 'configurations'" />
        <Credentials v-if="currentTab.type == 'credentials'" />
        <NotebookRepository v-if="currentTab.type == 'notebook-repository'" />
      </Tab>
    </TabManager>

    <div v-if="this.tabList.length == 0">
      <Home />
    </div>
  </div>
</template>

<script>
import ws from '@/services/ws-helper'

import TabManager from '@/components/Tabs/TabManager.vue'
import Tab from '@/components/Tabs/Tab.vue'

import Home from '@/views/Home.vue'
import Note from '@/components/Notebook/Note.vue'
import Interpreter from '@/components/Interpreter/InterpreterList.vue'

import Configurations from '@/components/Settings/Configurations.vue'
import Credentials from '@/components/Settings/Credentials.vue'
import NotebookRepository from '@/components/Settings/NotebookRepository.vue'

export default {
  name: 'tabManager',
  components: {
    TabManager,
    Tab,
    Home,
    Note,
    Interpreter,
    Configurations,
    Credentials,
    NotebookRepository
  },
  data () {
    return {

    }
  },
  computed: {
    tabList () {
      return this.$store.state.TabManagerStore.tabs
    }
  },
  mounted () {
    // Open notebook from the url
    this.openURLTab()
  },
  beforeRouteUpdate () {
    if (window.popStateDetected) {
      window.popStateDetected = false
      this.openURLTab()
    }
  },
  methods: {
    isListLoaded () {
      return ws.isListLoaded()
    },
    openURLTab () {
      if (this.isListLoaded()) {
        console.log('In openURLTab ' + this.$route.params.tabId)

        let tabId = this.$route.params && this.$route.params.tabId

        if (tabId) {
          let note = this.$store.getters.getNote(tabId)

          this.$root.executeCommand('tabs', 'open', {
            type: 'note',
            note: {
              id: tabId,
              name: note.name,
              path: note.path
            }
          })
        }
      } else {
        setTimeout(() => {
          this.openURLTab()
        }, 1000)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
</style>
