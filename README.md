## 使用Netty实现的http3服务器，可以同时启动http、https、http3服务器，用于测试比较

启动服务端时，会生成big.data（1GB）和small.data（1KB）,客户端会分别使用http,https,http3下载。如果是使用负载均衡
如nginx,clb等等，还可以测试http3通过udp转发透传，私有协议或者http使用quic监听器tcp做四层转发。
所以总共可测量的场景为
1. http-lb(http)-http 
2. https-lb(https)-http 
3. http3-lb(http3)-http 
4. http3-lb(udp)-http3 
5. quic-lb(quic)-tcp
6. http over quic-lb(quic)-tcp

通过测试clb的结果为:

1(无需加密)>2（clb硬件加速）>5(私有协议)>6(tcp四层转发)>3(clb硬件加速)>4(服务器需自身处理加解密)

在内网环境选择http进行明文传输，速度最快。在外网环境可以根据网路环境在http3和quic私有协议间进行切换
（考虑私有协议的开发难度与周期，我们可以选择http作为我们的私有协议，即http over quic）。

### 启动服务端
```shell
./gradew -Pserver run
```
### 启动客户端
```shell
./gradew run
```
