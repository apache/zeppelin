<template>
  <div>
    <div id="menu-bar">
      <ul class="main-menu">
        <!-- File Menu -->
        <li> File
          <ul>
            <li>
              <a
                @click="executeNotebookCommand('show-create')"
                href="javascript:void(0)"
              >
                New Notebook <span>Ctrl+N</span>
              </a>
            </li>
            <li>
              <a
                @click="executeNotebookCommand('show-import')"
                href="javascript:void(0)"
              >
                Import
              </a>
            </li>

            <li class="separator"></li>

            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('save')"
                href="javascript:void(0)"
              >
                Save <!--<span>Ctrl+S</span> -->
              </a>
            </li>

            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('manage-permissions')"
                href="javascript:void(0)"
              >
                Manage Permissions
              </a>
            </li>

            <li class="separator"></li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('export-json')"
                href="javascript:void(0)"
              >
                Explort
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('delete-temporary')"
                href="javascript:void(0)"
              >
                Move To Recycle Bin
              </a>
            </li>

            <li class="separator"></li>

            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('print')"
                href="javascript:void(0)"
              >
                Print
              </a>
            </li>
          </ul>
        </li>

        <!-- Edit Menu -->
        <li> Edit
          <ul>
            <li>
              <a
                @click="executeNotebookCommand('toggle-code')"
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                href="javascript:void(0)"
              >
                Show/Hide Code
              </a>
            </li>
            <li>
              <a
                @click="executeNotebookCommand('toggle-line-numbers')"
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                href="javascript:void(0)"
              >
                Show/Hide Line Numbers
              </a>
            </li>
            <li>
              <a
                @click="executeNotebookCommand('toggle-output')"
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                href="javascript:void(0)"
              >
                Show/Hide Outputs
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('find-and-replace')"
                href="javascript:void(0)"
              >
                Find And Replace...
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                @click="showConfirmClearOutput"
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                href="javascript:void(0)"
              >
                Clear All Outputs
              </a>
            </li>
          </ul>
        </li>

        <!-- View Menu -->
        <li> View
          <ul>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('show-toc')"
                href="javascript:void(0)"
              >
                Table Of Contents
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('show-version-control')"
                href="javascript:void(0)"
              >
                Version Control
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('show-notebook-info')"
                href="javascript:void(0)"
              >
                Notebook Info
              </a>
            </li>
          </ul>
        </li>

        <!-- Runtime Menu -->
        <li> Runtime
          <ul>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('run-all')"
                href="javascript:void(0)"
              >
                Run All
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('run-before')"
                href="javascript:void(0)"
              >
                Run Before
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('run-focused')"
                href="javascript:void(0)"
              >
                Run The Focused Paragraph
              </a>
            </li>
            <li>
              <a
                v-bind:class="{'disabled': !(isActiveNotebook)}"
                @click="executeNotebookCommand('run-after')"
                href="javascript:void(0)"
              >
                Run After
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                @click="openTab('interpreters')"
                href="javascript:void(0)"
              >
                Interpreters
              </a>
            </li>
          </ul>
        </li>

        <!-- Settings -->
        <li> Settings
          <ul>
            <li>
              <a
                @click="openTab('notebook-repository')"
                href="javascript:void(0)"
              >
                Notebook Repository
              </a>
            </li>
            <li>
              <a
                @click="openTab('credentials')"
                href="javascript:void(0)"
              >
                Credentials
              </a>
            </li>
            <li>
              <a
                @click="openTab('configurations')"
                href="javascript:void(0)"
              >
                Configuration
              </a>
            </li>
          </ul>
        </li>

        <!-- Tools Menu -->
        <li> Tools
          <ul>
            <li
              v-bind:class="{'disabled': !(isActiveNotebook)}"
              class="submenu"
            >
              Switch Theme
              <ul>
                <li>
                  <a
                    @click="executeNotebookCommand('switch-theme-light')"
                    href="javascript: void(0);"
                  >
                    Light
                  </a>
                </li>
                <li>
                  <a
                    @click="executeNotebookCommand('switch-theme-dark')"
                    href="javascript: void(0);"
                  >
                    Dark
                  </a>
                </li>
              </ul>
            </li>
            <li>
              <a
                href="javascript: void(0);"
                @click="showPreferences()"
              >
                Preferences
              </a>
            </li>
            <li>
              <a
                @click="executeCommand('show-keyboard-shortcuts')"
                href="javascript: void(0);"
              >
                Keyboard Shortcuts
              </a>
            </li>
          </ul>
        </li>

        <!-- Help Menu -->
        <li> Help
          <ul>
            <li>
              <a
                href="http://zeppelin.apache.org/community.html"
                target="_blank"
              >
                Mailing list
              </a>
            </li>
            <li>
              <a
                href="https://issues.apache.org/jira/browse/ZEPPELIN"
                target="_blank"
              >
                Report an issue
              </a>
            </li>
            <li>
              <a
                href="https://github.com/apache/zeppelin"
                target="_blank"
              >
                GitHub
              </a>
            </li>
            <li class="separator"></li>
            <li>
              <a
                href="http://zeppelin.apache.org/docs/0.9.0-SNAPSHOT/index.html"
                target="_blank"
              >
                Documentation
              </a>
            </li>
          </ul>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
import jQueryMenu from '@/components/thirdparty/jquery.menu.js'

export default {
  name: 'TopMenu',
  data: () => ({
  }),
  computed: {
    activeNotebook () {
      return this.isActiveNotebook && this.$store.state.TabManagerStore.currentTab
    },
    isActiveNotebook () {
      return (this.$store.state.TabManagerStore.currentTab &&
          this.$store.state.TabManagerStore.currentTab.type === 'notebook')
    }
  },
  mounted () {
    jQueryMenu.init()
  },
  methods: {
    showPreferences () {
      this.$store.dispatch('togglePreferences', true)
    },
    openTab (tabName) {
      this.$root.executeCommand('tabs', 'open', { type: tabName })
    },
    executeNotebookCommand (command) {
      if (this.isActiveNotebook || ['show-create', 'show-import'].indexOf(command) !== -1) {
        this.$root.executeCommand('notebook', command)
      }
    },
    showConfirmClearOutput () {
      if (!this.isActiveNotebook) {
        return
      }

      let that = this
      this.$confirm({
        title: that.$i18n.t('message.notebooks.clear_output_confirm'),
        onOk () {
          that.executeNotebookCommand('clear-output')

          that.$message.success(that.$18n.t('message.notebooks.clear_output_success'), 4)
        },
        onCancel () {}
      })
    }
  }
}
</script>

<style lang="scss">

</style>
