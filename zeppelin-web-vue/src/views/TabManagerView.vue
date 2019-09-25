<template>
  <div class="h100 w100">
    <TabManager
      v-if="this.tabList.length > 0"
      class="tabs-manager"
    >
      <Tab
        v-for="currentTab in this.tabList"
        :key="currentTab.id"
        :name="currentTab.name"
        :tabContent="currentTab"
      >
        <Notebook v-if="currentTab.type == 'notebook'" :note="currentTab" />
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
import TabManager from '@/components/Tabs/TabManager.vue'
import Tab from '@/components/Tabs/Tab.vue'

import Home from '@/views/Home.vue'
import Notebook from '@/components/Notebook/Notebook.vue'
import Interpreter from '@/components/Interpreter/InterpreterList.vue'

import Configurations from '@/components/Settings/Configurations.vue'
import Credentials from '@/components/Settings/Credentials.vue'
import NotebookRepository from '@/components/Settings/NotebookRepository.vue'

import { mapState } from 'vuex'

export default {
  name: 'tabManager',
  components: {
    TabManager,
    Tab,
    Home,
    Notebook,
    Interpreter,
    Configurations,
    Credentials,
    NotebookRepository
  },
  computed: mapState({
    tabList: state => state.TabManagerStore.tabs
  })
}
</script>

<style lang="scss" scoped>
</style>
