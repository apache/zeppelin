<template>
  <div class="configurations">
    <h6>Server Configurations</h6>

    <a-table
      :columns="columns"
      :dataSource="configurationList"
      size="small"
      :pagination="false"
      :scroll="{ y: 440 }"
    />

    <a-alert message="Note: For security reasons, some key/value pairs including passwords would not be shown." type="info" />
  </div>
</template>

<script>

export default {
  name: 'Configurations',
  data () {
    return {
      columns: [{
        title: 'Name',
        dataIndex: 'name'
      }, {
        title: 'Value',
        dataIndex: 'value'
      }]
    }
  },
  computed: {
    configurationList () {
      let configurations = []
      for (let key in this.$store.state.configurations) {
        configurations.push({
          name: key,
          value: this.$store.state.configurations[key]
        })
      }
      return configurations
    }
  },
  created () {

  },
  mounted () {
    this.getConfiguration()
  },
  methods: {
    getConfiguration () {
      this.$store.dispatch('getConfiguration')
    }
  }
}
</script>

<style lang="scss" scoped>
.configurations {
  padding: 10px;
}
</style>
