setupIde:
  mill mill.bsp.BSP/install
  mill --import ivy:com.lihaoyi::mill-contrib-bloop:  mill.contrib.bloop.Bloop/install

test:
  mill itest.test -j 0