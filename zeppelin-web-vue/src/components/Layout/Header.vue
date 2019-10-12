<template>
  <div id="header">
    <div style="display: flex">
      <router-link
        to="/"
        class="logo pt-2 pl-2"
      >
        <img alt="Zeppelin logo" src="@/assets/zepLogoW.png">
      </router-link>

      <h5>
        Zeppelin Notebook
      </h5>

      <TopMenu />

      <Connectivity class="connectivity"/>
    </div>
  </div>
</template>

<script>
import TopMenu from './TopMenu.vue'
import Connectivity from './Connectivity.vue'

export default {
  name: 'Header',
  components: { TopMenu, Connectivity }
}
</script>

<style lang="scss" scoped>
#header {
  height: 40px;
  margin: 0;
  padding: 0;
  background: #2C2C2C;
  color: #FFFFFF;

  h5 {
    color: #FFFFFF;
    font-style: normal;
    font-weight: 600;
    font-size: 16px;
    border-right: 0.5px solid rgba(211, 211, 211, 0.2);
    vertical-align: middle;
    line-height: 20px;
    padding-left: 12px;
    padding-right: 20px;
    margin-right: 20px;
    margin-top: 10px;
  }

  .logo {
    display: block;
    width: 50px;

    img {
      height: 20px;
    }
  }

  .connectivity {
    position: absolute;
    right: 0;
    top: 7px;
  }
}
</style>
