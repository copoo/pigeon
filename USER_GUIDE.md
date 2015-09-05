## Pigeon开发指南
______

Pigeon是一个分布式服务通信框架（RPC），在大众点评内部广泛使用，是大众点评最基础的底层框架之一。

### 主要特色

除了支持spring schema等配置方式，也支持代码annotation方式发布服务、引用远程服务，并提供原生api接口的用法。

支持http协议，方便非java应用调用pigeon的服务。

序列化方式除了hessian，还支持fst、protostuff。

提供了服务器单机控制台pigeon-console，包含单机服务测试工具。

创新的客户端路由策略，提供服务预热功能，解决线上流量大的service重启时大量超时的问题。

记录每个请求的对象大小、返回对象大小等监控信息。

服务端可对方法设置单独的线程池进行服务隔离，可配置客户端应用的最大并发数进行限流。


### 依赖

pigeon依赖JDK1.6+

pom依赖定义：

		<dependency>
		<groupId>com.dianping</groupId>
		<artifactId>pigeon</artifactId>
		<version>2.6.0-SNAPSHOT</version>
		</dependency>

pigeon在运行时可能会依赖以下jar包，如果有必要，需要应用自行加上以下jar(版本建议高于或等于以下基础版本)：

		<!-- 监控框架依赖，下面的cat依赖是可选的，如果不依赖cat则默认不会有监控功能，如果想接入大众点评的监控框架cat（已经开源），需增加以下依赖（pigeon-monitor-cat代码在https://github.com/wu-xiang/pigeon-monitor-cat） -->
		<dependency>
		<groupId>com.dianping</groupId>
		<artifactId>pigeon-monitor-cat</artifactId>
		<version>1.0.0-SNAPSHOT</version>
		</dependency>
		<dependency>
		<groupId>com.dianping.cat</groupId>
		<artifactId>cat-core</artifactId>
		<version>1.3.6-SNAPSHOT</version>
		</dependency>
		
		<!-- 配置框架依赖，下面的lion依赖是可选的，如果不依赖lion则会默认通过本地文件加载配置，如果想接入大众点评的配置框架lion(尚未开源)，需增加以下依赖（pigeon-config-lion代码在https://github.com/wu-xiang/pigeon-config-lion） -->
		<dependency>
		<groupId>com.dianping</groupId>
		<artifactId>pigeon-config-lion</artifactId>
		<version>1.0.0-SNAPSHOT</version>
		</dependency>
		<dependency>
		<groupId>com.dianping.lion</groupId>
		<artifactId>lion-client</artifactId>
		<version>0.5.3</version>
		</dependency>

		<!-- 加入spring，版本根据自身需要设置 -->
		<dependency>
		<groupId>org.springframework</groupId>
		<artifactId>spring-context</artifactId>
		<version>2.5.6</version>
		</dependency>
		
		<!-- 如果是非tomcat项目需要自行加入servlet-api的jar -->
		<dependency>
		<groupId>org.mortbay.jetty</groupId>
		<artifactId>servlet-api</artifactId>
		<version>2.5-20081211</version>
		</dependency>

### 准备工作

如果是在外部公司使用开源版本pigeon，需要关注此章节，进行一些准备工作：

1、下载代码后，通过maven构建项目：

git clone git地址 pigeon-parent

cd pigeon-parent

mvn clean install -DskipTests

2、环境准备

a、zookeeper安装
pigeon内部使用zookeeper作为注册中心，需要安装好zookeeper集群。

b、配置pigeon的zookeeper集群地址
如未使用大众点评配置框架lion，需在应用代码resources/config/pigeon.properties里（也可以在绝对路径/data/webapps/config/pigeon.properties里）设置注册中心zookeeper地址：

pigeon.registry.address=10.1.1.1:2181,10.1.1.2:2181,10.1.1.3:2181,10.1.1.4:2181,10.1.1.5:2181


c、配置摘除服务的脚本：

由于pigeon内部是在zookeeper里使用持久化节点，如果非正常关闭jvm，不会从zookeeper集群里摘除相应的本机服务的ip、port，需要在关闭jvm脚本里（比如tomcat的shutdown.sh脚本）加入以下调用：

/usr/bin/curl -s --connect-timeout 5  --speed-time 6 --speed-limit 1 "http://127.0.0.1:4080/services.unpublish"

该脚本内部会等待3秒，如果成功会返回ok，等该脚本执行成功再关闭jvm

d、应用名称配置：

在应用代码resources/META-INF/app.properties文件里设置

app.name=xxx

代表此应用名称为xxx，定义应用名称是基于规范应用的考虑


### 快速入门

本文档相关示例代码可以参考pigeon-demo模块：


1、定义服务

定义服务接口: (该接口需单独打包，在服务提供方和调用方共享)

EchoService.java

		package com.dianping.pigeon.demo;
		public interface EchoService {
			public String echo(String name);
		}

在服务提供方实现接口：(对服务调用方隐藏实现)

EchoServiceImpl.java

		package com.dianping.pigeon.demo.provider;
		import com.dianping.pigeon.demo.EchoService;
		public class EchoServiceImpl implements EchoService {
			public String echo(String name) {
				return "Hello " + name;
			}
		}

2、服务提供者

这里先介绍传统spring方式，后边章节会介绍annotation方式、spring schema定义方式、api方式。

Spring配置声明暴露服务：

provider.xml
services属性下的key是服务全局唯一的标识url（如果一个远程服务未特别设置，url默认是服务接口类名），value是引用的服务bean
port属性可不指定

		<bean class="com.dianping.pigeon.remoting.provider.config.spring.ServiceBean"
		init-method="init">
		<property name="services">
		<map>
		<entry key="http://service.dianping.com/demoService/echoService_1.0.0"
		value-ref="echoServiceImpl" />
		</map>
		</property>
		<property name="port">
		<value>5008</value>
		</property>
		</bean>
		
		<bean id="echoServiceImpl" class="com.dianping.pigeon.demo.provider.EchoServiceImpl" />
		
加载Spring配置：

Provider.java

		import org.springframework.context.support.ClassPathXmlApplicationContext;
		public class Provider {
		public static void main(String[] args) throws Exception {
			ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"provider.xml"});
			context.start();
			System.in.read(); // 按任意键退出
		}
		}


3、服务调用者

这里先介绍传统spring方式，后边章节会介绍annotation方式、spring schema定义方式、api方式。

通过Spring配置引用远程服务：

invoker.xml

		<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
		<property name="url" value="http://service.dianping.com/demoService/echoService_1.0.0" /><!-- 服务全局唯一的标识url，默认是服务接口类名，必须设置 -->
		<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" /><!-- 接口名称，必须设置 -->
		<property name="timeout" value="2000" /><!-- 超时时间，毫秒，默认5000，建议自己设置 -->
		<property name="serialize" value="hessian" /><!-- 序列化，hessian/fst/protostuff，默认hessian，可不设置-->
		<property name="callType" value="sync" /><!-- 调用方式，sync/future/callback/oneway，默认sync，可不设置 -->
		<property name="cluster" value="failfast" /><!-- 失败策略，快速失败failfast/失败转移failover/失败忽略failsafe/并发取最快返回forking，默认failfast，可不设置 -->
		<property name="timeoutRetry" value="false" /><!-- 是否超时重试，默认false，可不设置 -->
		<property name="retries" value="1" /><!-- 重试次数，默认1，可不设置 -->
		</bean>
		
加载Spring配置，并调用远程服务：

Invoker.java

		import org.springframework.context.support.ClassPathXmlApplicationContext;
		import com.dianping.pigeon.demo.EchoService;
		public class Invoker {
			public static void main(String[] args) throws Exception {
				ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {“invoker.xml"});
			context.start();
				EchoService echoService = (EchoService)context.getBean(“echoService"); // 获取远程服务代理
				String hello = echoService.echo("world");
				System.out.println( hello );
			}
		}


### annotation编程方式

annotation方式的编程无需在spring里定义每个bean，但仍需依赖spring，具体使用方式如下：

1、服务提供者
EchoService是一个远程服务的接口：

		public interface EchoService {
			String echo(String input);
		}
		
在服务端需要实现这个服务接口，服务实现类上需要加上@Service（com.dianping.pigeon.remoting.provider.config.annotation.Service）：

		@Service
		public class EchoServiceAnnotationImpl implements EchoService {
		@Override
		public String echo(String input) {
			return "annotation service echo:" + input;
		}
		}
		
除此之外，只需要在spring配置里加上pigeon:annotation配置：

		<beans xmlns="http://www.springframework.org/schema/beans"
		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
		xmlns:tx="http://www.springframework.org/schema/tx" xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
		http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd
		http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
		              http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">
		<pigeon:annotation />
		<!-- 默认只扫描com.dianping包，如果非此包下的服务需要自定义package属性，多个package以逗号,分隔-->
		</beans>

@Service在pigeon内部的定义如下：

		public @interface Service {
			Class<?> interfaceClass() default void.class;
			String url() default "";
			String version() default "";
			String group() default "";
			int port() default 4040;
			boolean autoSelectPort() default true;
			boolean useSharedPool() default true;
			int actives() default 0;
		}

2、服务调用者
假设在客户端有一个AnnotationTestService，需要引用远程的EchoService服务，只需要在field或method上加上@Reference：

		public class AnnotationTestService {
		
		@Reference(timeout = 1000)
		private EchoService echoService;
		
		public String testEcho(String input) {
			return echoService.echo(input);
		}
		}
		
只需要在spring配置里加上pigeon:annotation配置：

		<beans xmlns="http://www.springframework.org/schema/beans"
		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
		xmlns:tx="http://www.springframework.org/schema/tx" xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
		http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd
		http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
		              http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">
		<pigeon:annotation />
		<!-- 默认只扫描com.dianping包，如果非此包下的服务需要自定义package属性，多个package以逗号,分隔-->
		<bean id="annotationTestService"
		class="com.dianping.pigeon.demo.invoker.annotation.AnnotationTestService" />
		</beans>

@Reference定义：

		public @interface Reference {
			Class<?> interfaceClass() default void.class;
			String url() default "";
			String protocol() default "default";
			String serialize() default "hessian";
			String callType() default "sync";
			int timeout() default 5000;
			String callback() default "";
			String loadbalance() default "weightedAutoaware";
			String cluster() default "failfast";
			int retries() default 1;
			boolean timeoutRetry() default false;
			String version() default "";
			String group() default "";
		}

### spring schema配置方式

1、服务端spring配置

		<?xml version="1.0" encoding="UTF-8"?>
		<beans xmlns="http://www.springframework.org/schema/beans"
		      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		xmlns:context="http://wspringframework.org/schema/context"
		      xmlns:tx="http://www.spamework.org/schema/tx"
		xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		      xsi:schemaLocation="http://www.springframework.org/schema/beans
		http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
		                              http://www.springframework.org/schema/context
		http://www.springframework.org/schema/context/spring-context-2.5.xs                  http://www.springframework.org/schema/tx
		http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
		                              http://code.dianping.com/schema/pigeon
		http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd"
		      default-autowire="byName">
		<bean id="echoServiceImpl" class="com.dianping.pigeon.demo.provider.EchoServiceImpl"
		/>
		<pigeon:service id="echoService"
		      interface="com.dianping.pigeon.demo.EchoService" ref="echoServiceImpl" />
		</beans>

也可以指定服务url（代表这个服务的唯一性标识，默认是接口类名）和port等属性：

		<bean id="echoServiceImpl" class="com.dianping.pigeon.demo.provider.EchoServiceImpl" />
		<pigeon:service id="echoService" url="http://service.dianping.com/demoService/echoService_1.0.0"
		interface="com.dianping.pigeon.demo.EchoService" port="4040" ref="echoServiceImpl" />

2、客户端spring配置

		<?xml version="1.0" encoding="UTF-8"?>
		<beans xmlns="http://www.springframework.org/schema/beans"
		      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		xmlns:context="http://www.springframework.org/schema/context"
		      xmlns:tx="http://www.springframework.org/schema/tx"
		xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		      xsi:schemaLocation="http://www.springframework.org/schema/beans
		http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
		                              http://www.springframework.org/schema/context
		http://www.springframework.org/schema/context/spring-context-2.5.xsd
		                              http://www.springframework.org/schema/tx
		http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
		              http://code.dianping.com/schema/pigeon
		http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">
		<pigeon:reference id="echoService" timeout="1000"
		protocol="http" serialize="hessian" callType="sync"
		      interface="com.dianping.pigeon.demo.EchoService" />
		<!-- timeout-超时时间，毫秒-->
		<!-- callType-调用方式，sync/future/callback/oneway，默认sync -->
		<!-- protocol-协议，default/http，默认default -->
		<!-- serialize-序列化，hessian/fst/protostuff，默认hessian -->
		<!-- cluser调用失败策略，快速失败failfast/失败转移failover/失败忽略failsafe/并发取最快返回forking，默认failfast  -->
		<!-- timeoutRetry是否超时重试，在cluster为failover时有效，默认false  -->
		<!-- retries超时重试次数，在cluster为failover时有效  -->
		<!-- interface-服务接口名称 -->
		<!-- url-服务全局唯一的标识url -->
		<!-- callback-服务回调对象 -->
		<!-- loadBalance-负载均衡类型，autoaware/roundRobin/random，默认autoaware -->
		<bean id="echoServiceCallback"
		class="com.dianping.pigeon.demo.invoker.EchoServiceCallback" />
		
		<pigeon:reference id="echoServiceWithCallback"  timeout="1000"
		protocol="http" serialize="hessian" callType="sync"
		      interface="com.dianping.pigeon.demo.EchoService"
		      callback="echoServiceCallback" />
		</beans>

也可以指定服务url（代表这个服务的唯一性标识，默认是接口类名）属性：

		<pigeon:reference id="echoService" url="http://service.dianping.com/demoService/echoService_1.0.0"  timeout=”1000”
		      interface="com.dianping.pigeon.demo.EchoService" />
		<bean id="echoServiceCallback"
		class="com.dianping.pigeon.demo.invoker.EchoServiceCallback" />
		<pigeon:reference id="echoServiceWithCallback"
		url="http://service.dianping.com/demoService/echoService_1.0.0" timeout=”1000”
		      interface="com.dianping.pigeon.demo.EchoService" callType="callback"
		      callback="echoServiceCallback" />

### api编程方式

1、服务提供者

Provider.java

		public class Provider {
		public static void main(String[] args) throws Exception {
			ServiceFactory.addService(EchoService.class, new EchoServiceImpl());
			System.in.read(); // 按任意键退出
		}
		}
		
如需自定义服务url（代表这个服务的唯一性标识，默认是接口类名）或端口等参数，可以参考以下代码：

		ServiceFactory.publishService("http://service.dianping.com/demoService/echoService_1.0.0", EchoService.class, new EchoServiceImpl(), 4040);

更详细的api接口可以参考ServiceFactory类的api详细说明。

2、服务调用者

Invoker.java

		public class Invoker {
			public static void main(String[] args) throws Exception {
				EchoService echoService = ServiceFactory.getService(EchoService.class); // 获取远程服务代理
				String hello = echoService.echo("world");
				System.out.println( hello );
			}
		}
		
如果要调用的服务定义了特定的url（代表这个服务的唯一性标识，默认是接口类名），需要客户端指定服务url，可以参考如下代码：

		EchoService echoService = ServiceFactory.getService("http://service.dianping.com/demoService/echoService_1.0.0", EchoService.class, 2000); // 获取远程服务代理
		String hello = echoService.echo("world");
		System.out.println( hello );

如果要程序指定序列化方式或协议类型，可以参考如下代码：

		InvokerConfig<EchoService> config = new InvokerConfig<EchoService>(EchoService.class);
		config.setProtocol(InvokerConfig.PROTOCOL_DEFAULT);
		config.setSerialize(InvokerConfig.SERIALIZE_HESSIAN);
		EchoService service = ServiceFactory.getService(config);
		String hello = service.echo("world");
		System.out.println( hello );
		
更详细的api接口可以参考ServiceFactory类的api详细说明。

3、ServiceFactory接口：

		public static <T> T getService(Class<T> serviceInterface) throws RpcException
		public static <T> T getService(Class<T> serviceInterface, int timeout) throws RpcException
		public static <T> T getService(Class<T> serviceInterface, ServiceCallback callback) throws RpcException
		public static <T> T getService(Class<T> serviceInterface, ServiceCallback callback, int timeout)
		throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface) throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface, int timeout) throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface, ServiceCallback callback) throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface, ServiceCallback callback, int timeout)
		throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param serviceInterface
		* @param service
		* @throws RpcException
		*/
		public static <T> void addService(Class<T> serviceInterface, T service) throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param url
		* @param serviceInterface
		* @param service
		* @throws RpcException
		*/
		public static <T> void addService(String url, Class<T> serviceInterface, T service) throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param url
		* @param serviceInterface
		* @param service
		* @param port
		* @throws RpcException
		*/
		public static <T> void addService(String url, Class<T> serviceInterface, T service, int port) throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void addService(ProviderConfig<T> providerConfig) throws RpcException
		/**
		* add the services to pigeon and publish these services to registry
		*
		* @param providerConfigList
		* @throws RpcException
		*/
		public static void addServices(List<ProviderConfig<?>> providerConfigList) throws RpcException
		/**
		* publish the service to registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void publishService(ProviderConfig<T> providerConfig) throws RpcException
		/**
		* publish the service to registry
		*
		* @param url
		* @throws RpcException
		*/
		public static <T> void publishService(String url) throws RpcException
		/**
		* unpublish the service from registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void unpublishService(ProviderConfig<T> providerConfig) throws RpcException
		/**
		* unpublish the service from registry
		*
		* @param url
		* @throws RpcException
		*/
		public static <T> void unpublishService(String url) throws RpcException
		/**
		* unpublish all pigeon services from registry
		*
		* @throws RpcException
		*/
		public static void unpublishAllServices() throws RpcException
		/**
		* publish all pigeon services to registry
		*
		* @throws RpcException
		*/
		public static void publishAllServices() throws RpcException
		/**
		* remove all pigeon services, including unregister these services from
		* registry
		*
		* @throws RpcException
		*/
		public static void removeAllServices() throws RpcException
		/**
		* remove the service from pigeon, including unregister this service from
		* registry
		*
		* @param url
		* @throws RpcException
		*/
		public static void removeService(String url) throws RpcException
		/**
		* remove the service from pigeon, including unregister this service from
		* registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void removeService(ProviderConfig<T> providerConfig) throws RpcException
		public static void setServerWeight(int weight) throws RegistryException
		public static void online() throws RegistryException
		public static void offline() throws RegistryException

### 序列化支持

pigeon2支持多种序列化方式，序列化方式只需要在客户端调用时通过serialize属性指定，一般情况推荐兼容性最好的hessian。
如果需要自行设计序列化方式，可以继承com.dianping.pigeon.remoting.common.codec.DefaultAbstractSerializer类来定义自己的序列化类，并通过SerializerFactory.registerSerializer(byte serializerType, Serializer serializer)接口将自定义的序列化类注册进来。

### http协议支持

pigeon目前支持2种协议：default和http。

default是pigeon默认的基于tcp方式的调用方式。

而pigeon也支持http调用，这样可以允许非java的应用调用pigeon2的服务。对于http调用，服务端不需要任何修改，任何一个pigeon2的服务启动之后，都会同时支持目前默认的基于tcp的和基于http的服务调用，只需要客户端修改配置即可实现http方式的调用。

http协议的默认端口是4080，目前不可配置，如果被占用，会自动选择其他端口。

如果想通过http调用pigeon服务，可以通过http发送post请求调用pigeon服务，可以采用json或hessian的序列化格式:

a) 可以将请求内容post到http://ip:4080/service，并且在header里设置serialize参数为7或2。

b) 如果是json序列化可以post到http://ip:4080/service?serialize=7，如果是hessian序列化请post到http://ip:4080/service?serialize=2。

POST方式：
post地址：http://ip:4080/service?serialize=7
json请求：
{"seq":-985,"serialize":7,"callType":1,"timeout":1000,"methodName":"echo","parameters":["echoService_492"],"messageType":2,"url":"com.dianping.pigeon.demo.EchoService"}
返回：
{"seq":-985,"messageType":2,"context":null,"exception":null,"response":"echo:echoService_492"}
如果参数List<T>类型：
json请求：
{"seq":-146,"serialize":7,"callType":1,"timeout":2000,"methodName":"getUserDetail","parameters":[["java.util.List",[{"@class":"com.dianping.pigeon.demo.UserService$User","username":"user_73"},{"@class":"com.dianping.pigeon.demo.UserService$User","username":"user_74"}]],false],"messageType":2,"url":"com.dianping.pigeon.demo.UserService"}
返回：
{"seq":-146,"messageType":2,"context":null,"exception":null,"response":["[Lcom.dianping.pigeon.demo.UserService$User;",[{"username":"user_73","email":null,"password":null},{"username":"user_74","email":null,"password":null}]]}

GET方式：
http://ip:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo&parameterTypes=java.lang.String&parameters=abc
url参数是服务地址
method是服务方法
parameterTypes是服务方法method的参数类型，如果是多个参数就写多个parameterTypes
parameters是参数值，多个参数值就写多个parameters（特别提醒：如果参数类型是enum类型，参数值要传某个enum值，请传递该值在enum里的定义顺序，如enum的第1个值就传0，第2个值就传1）
如果是多个参数，比如某个方法：String echo2(String input, int size);
url示例：
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo2&parameterTypes=java.lang.String&parameters=wux&parameterTypes=int&parameters=2
如果服务方法参数类型是Collection泛型，如List<User>，需要在参数值指定@class类型，比如getUserDetail(java.util.List,boolean)这个方法：
[{"@class":"com.dianping.pigeon.demo.UserService$User","username":"user_73"},{"@class":"com.dianping.pigeon.demo.UserService$User","username":"user_74"}]


以上json格式需符合jackson的json规范，如果不清楚一个对象对应的json字符串，pigeon提供接口可以得到对象转换后的json字符串。

		public static void main(String[] args) {
			User user = new User();
			user.setUsername("scott");
			List<User> users = new ArrayList<User>();
			users.add(user);
			JacksonSerializer serializer = new JacksonSerializer();
			String str = serializer.serializeObject(users);
			System.out.println(str);
		}

### 服务测试工具

pigeon提供了服务测试的工具，测试工具基于pigeon的http协议(默认在4080端口)，可以访问每一台服务器的url：

http://ip:4080/services
会列出该服务器上所有pigeon服务列表，对于每一个服务方法，可以在右侧输入json格式的参数，进行invoke调用，获取json格式的服务结果，如下图
QQ图片20140226095502.jpg
如果不清楚一个对象对应的json字符串，可以参考前面一节，pigeon提供接口可以得到对象转换后的json字符串。
在线上环境进行测试时，需要输入验证码，验证码可以从该ip的pigeon日志文件中获取，请务必谨慎使用该测试工具，以免人为失误影响线上数据。
如果服务方法参数类型是Collection泛型，如List<User>，需要在参数值指定@class类型，比如getUserDetail(java.util.List,boolean)这个方法，第一个参数需要填的参数值为[{"@class":"com.dianping.pigeon.demo.UserService$User","username":"wux","email":"scott@dianping.com"}]
如果服务方法参数类型是Map泛型，如Map<User, Double>，需要符合这种格式：{"{\"@class\":\"com.dianping.pigeon.demo.UserService$User\",\"username\":\"w\",\"email\":null,\"password\":null}":4.5,"{\"username\":\"x\",\"email\":null,\"password\":null}":3.5}
访问http://ip:4080/services.json可以返回该服务器所有服务列表的json内容。
访问http://ip:4080/invoke.json可以通过get方式测试服务，例如：
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo&parameterTypes=java.lang.String&parameters=abc
url参数是服务地址
method是服务方法
parameterTypes是服务方法method的参数类型，如果是多个参数就写多个parameterTypes
parameters是参数值，多个参数值就写多个parameters（特别提醒：如果参数类型是enum类型，参数值要传某个enum值，请传递该值在enum里的定义顺序，如enum的第1个值就传0，第2个值就传1）
如果是多个参数，比如某个方法：String echo2(String input, int size);
url示例：
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo2&parameterTypes=java.lang.String&parameters=wux&parameterTypes=int&parameters=2

方法echo2(java.util.Map<User, Double>,int):
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo2&parameterTypes=java.util.Map&parameters={"{\"@class\":\"com.dianping.pigeon.demo.UserService$User\",\"username\":\"w\",\"email\":null,\"password\":null}":4.5,"{\"@class\":\"com.dianping.pigeon.demo.UserService$User\",\"username\":\"x\"}":3.5}&parameterTypes=int&parameters=3&direct=false

如果需要每次调用都记录cat日志，需要带上direct=false参数
http://ip:4080/services.status可以测试服务健康状况

### 配置负载均衡策略

配置客户端的loadBalance属性，目前可以是random/roundRobin/weightedAutoware这几种类型，默认是weightedAutoware策略，一般场景不建议修改。
	
		<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean"
		init-method="init">
		<property name="url"
		value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
		<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
		<property name="callType" value="sync" />
		<property name="timeout" value="1000" />
		<property name="loadBalance"
		value="weightedAutoware" />
		</bean>

### 客户端配置某个方法的超时时间

pigeon支持客户端调用某个服务接口时，对整个服务的超时时间进行设置，也可以对该服务接口的某个方法设置单独的超时时间，没有配置超时的方法会以服务级别的超时时间为准。
		
		<beans xmlns="http://www.springframework.org/schema/beans"
		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
		xmlns:tx="http://www.springframework.org/schema/tx" xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
		http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd
		http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
		              http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">
		<pigeon:reference id="echoService" timeout="1000"
		url="http://service.dianping.com/com.dianping.pigeon.demo.EchoService"
		interface="com.dianping.pigeon.demo.EchoService">
		<pigeon:method name="echo" timeout="2000" />
		</pigeon:reference>
		
如果想设置当前线程下一个pigeon方法调用的超时时间，可以调用

		InvokerHelper.setTimeout(200);


### 服务隔离与限流

1、配置服务方法级别的最大并发数
pigeon支持服务端对某个服务接口的方法的最大并发数进行配置，这样可以隔离每个服务方法的访问，防止某些方法执行太慢导致服务端线程池全部卡住的问题。
只需要设置useSharedPool为false，pigeon就会为每个方法设置独立的线程池执行请求。
如果想单独设置某个方法的最大并发数，需要设置这个方法的actives属性。
如果并发超过设置的最大并发数，服务端会抛出com.dianping.pigeon.remoting.common.exception.RejectedException异常，客户端也会收到这个异常。
	
		<beans xmlns="http://www.springframework.org/schema/beans"
		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
		xmlns:tx="http://www.springframework.org/schema/tx" xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
		http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd
		http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
		http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd"
		default-autowire="byName">
		<!-- 如果下面的每个服务的useSharedPool设置为false，则共享池几个参数可以设置小一点 -->
		<pigeon:server id="pigeonServer" corePoolSize="${pigeon-test.sharedpool.coresize}" port="${pigeon-test.port}"
		maxPoolSize="${pigeon-test.sharedpool.maxsize}" workQueueSize="${pigeon-test.sharedpool.queuesize}" />
		<!-- useSharedPool设置为false代表每个方法会使用独立的线程池，方法级别的actives属性代表该方法的最大请求并发数-->
		<pigeon:service useSharedPool="${pigeon-test.sharedpool.use}" server="pigeonServer"
		url="http://service.dianping.com/com.dianping.pigeon.demo.EchoService"
		interface="com.dianping.pigeon.demo.EchoService" ref="defaultEchoServiceImpl">
		<pigeon:method name="echo" actives="${pigeon-test.methodpool.actives.echo}" />
		<pigeon:method name="echo2" actives="${pigeon-test.methodpool.actives.echo2}" />
		</pigeon:service>
		<!-- 服务级别的actives属性代表该服务的最大请求并发数，该服务将采用独立线程池，这个服务如果配置method，则actives属性无效-->
		<pigeon:service useSharedPool="false" server="pigeonServer"
		url="http://service.dianping.com/com.dianping.pigeon.demo.UserService" actives="100"
		interface="com.dianping.pigeon.demo.UserService" ref="defaultEchoServiceImpl">
		</pigeon:service>
		
以上配置里actives、workQueueSize、corePoolSize、maxPoolSize，如果使用点评的lion配置框架，均可以通过lion动态在线设置实时生效

2、限制某个客户端应用的最大并发数
pigeon也支持在服务端配置某个客户端应用的最大并发数
a、首先需要在应用lion里配置开关打开，例如deal-service项目要配置以下lion配置：
deal-service.pigeon.provider.applimit.enable=true
b、配置客户端应用对应的最大并发数：
pigeon.provider.applimit=tuangou-web:100,xxx:50,yyy:100
上面的客户端应用名称是标准统一的项目名称，以CMDB里为准。
并发数一般看是pigeon服务端线程池最大并发多少，比如500个最大并发，根据这个数字再看业务上平时一般客户端应用的比例来决定每个应用大概的最大并发数。
目前只能限制客户端应用总的最大并发数，不能精确到某个应用的某个方法，后续版本会支持。
以上配置第一次配置了之后，均可以通过lion动态在线设置实时生效


### 服务预热

pigeon提供了客户端服务预热功能，当某个服务端机器重启后，客户端会接收到该事件，客户端的请求将会把更多的请求发送到该服务的其他机器，只会发送少量请求到重启的服务端机器，然后逐渐会将发送给该机器的请求增加，经过几十秒的预热过程后，直到与其他机器请求数差不多。

### 配置客户端调用模式

在pigeon内部，客户端调用远程服务有4种模式（sync/future/callback/oneway），例如spring编程方式下只需要配置callType属性：
		
		<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
			<property name="url" value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
			<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
			<property name="callType" value="sync" />
			<property name="timeout" value="1000" />
		</bean>

a、sync
同步调用，客户端线程会阻塞等待返回结果，默认设置是sync模式。

b、oneway
客户端只是将请求传递给pigeon，pigeon提交给服务端，客户端也不等待立即返回，服务端也不会返回结果给客户端，这种方式一般都是没有返回结果的接口调用。​

c、future
客户端将请求提交给pigeon后立即返回，不等待返回结果，由pigeon负责等待返回结果，客户端可以自行决定何时何地来取返回结果，代码示例：
//调用ServiceA的method1
serviceA.method1("aaa");
//获取ServiceA的method1调用future状态
Future future1OfServiceA = ServiceFutureFactory.getFuture();
//调用ServiceA的method2
serviceA.method2("bbb");
//获取ServiceA的method2调用future状态
Future future2OfServiceA = ServiceFutureFactory.getFuture();
//调用ServiceB的method1
serviceB.method1("ccc");
//获取ServiceB的method1调用future状态
Future future1OfServiceB = ServiceFutureFactory.getFuture();
//获取ServiceA的method2调用结果
Object result2OfServiceA = future2OfServiceA.get();
//获取ServiceA的method1调用结果
Object result1OfServiceA = future1OfServiceA.get();
//获取ServiceB的method1调用结果
Object result1OfServiceB = future1OfServiceB.get();
最后的get()调用顺序由业务自行决定，操作总共花费的时间，大致等于耗时最长的服务方法执行时间。
除了get();接口也可以使用get(timeout);指定超时时间。

d、callback
回调方式，客户端将请求提交给pigeon后立即返回，也不等待返回结果，它与future方式的区别是，callback必须提供一个实现了pigeon提供的ServiceCallback接口的回调对象给pigeon，pigeon负责接收返回结果并传递回给这个回调对象，代码示例：
spring配置文件：

		<bean id="echoServiceWithCallback" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean"
		init-method="init">
		<property name="url"
		value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
		<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
		<property name="callType" value="callback" />
		<property name="timeout" value="1000" />
		<property name="callback" ref="echoServiceCallback" />
		</bean>
		<bean id="echoServiceCallback" class="com.dianping.pigeon.demo.invoker.EchoServiceCallback" />
		
调用代码：

		import org.springframework.context.support.ClassPathXmlApplicationContext;
		import com.dianping.pigeon.demo.EchoService;
		public class Invoker {
			public static void main(String[] args) throws Exception {
				ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {“invoker.xml"});
				context.start();
				EchoService echoServiceWithCallback = (EchoService)context.getBean(“echoServiceWithCallback"); // 获取远程服务代理
				String hello = echoServiceWithCallback.echo("world");
				System.out.println( hello );
			}
		}
		
Callback类：

		public class EchoServiceCallback implements ServiceCallback {
			private static final Logger logger = LoggerLoader.getLogger(EchoServiceCallback.class);
			@Override
			public void onSuccess(Object result) {
				System.out.println("callback:" + result);
			}
			@Override
			public void onFailure(Throwable exception) {
				logger.error("", exception);
			}
		}
		
### 配置客户端集群策略模式

客户端配置cluster属性：

		<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean"
		init-method="init">
		<property name="url"
		value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
		<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
		<property name="callType" value="sync" />
		<property name="timeout" value="1000" />
		<property name="cluster" value="failfast" /><!-- 失败策略，快速失败failfast/失败转移failover/失败忽略failsafe/并发取最快返回forking，默认failfast -->
		<property name="timeoutRetry" value="false" /><!-- 是否超时重试，默认false -->
		<property name="retries" value="1" /><!-- 重试次数，默认1 -->

failfast-调用服务的一个节点失败后抛出异常返回，可以同时配置重试timeoutRetry和retries属性
failover-调用服务的一个节点失败后会尝试调用另外的一个节点，可以同时配置重试timeoutRetry和retries属性
failsafe-调用服务的一个节点失败后不会抛出异常，返回null，后续版本会考虑按配置默认值返回
forking-同时调用服务的所有可用节点，返回调用最快的节点结果数据

### 如何传递自定义参数

1、简单的客户端A->服务端B的一级调用链路的参数传递
客户端：
String url = "http://service.dianping.com/com.dianping.pigeon.demo.EchoService";
EchoService service = ServiceFactory.getService(url, EchoService.class);
...
ContextUtils.putRequestContext("key1", "1");
System.out.println("service result:" + service.echo(input));

服务端：
public String echo(String input) {
System.out.println(ContextUtils.getLocalContext("key1"));
return "echo:" + input;
}

2、服务端B->客户端A的参数传回
服务端：
ContextUtils.putResponseContext("key1", "1");
客户端：
ContextUtils.getResponseContext("key1");

3、全链路传递
如果需要在全链路传递对象，如A->B->C->D，需要使用以下接口：
在A发送请求端：ContextUtils.putGlobalContext("key1", "1");
在D接收请求端：ContextUtils.getGlobalContext("key1");

### 如何指定固定ip:port访问pigeon服务

客户端可以配置只连某台服务器进行pigeon服务调试，比如alpha环境可以在你的classpath下配置config/pigeon_alpha.properties文件（如果是beta环境设置pigeon_qa.properties，如果是dev环境设置pigeon_dev.properties），实现只访问192.168.0.1:4040提供的pigeon服务：http://service.dianping.com/com.dianping.pigeon.demo.EchoService=192.168.0.1:4040
在pigeon1.x中支持的config/applicationContext.properties文件类似上述配置，但applicationContext.properties只在dev和alpha环境生效，其他环境还是从zookeeper中获取服务地址。
如果要在代码层面设置，需要在调用服务前指定以下代码：
      线程级别每次请求前设置：InvokerHelper.setAddress("192.168.0.1:4040");该方式请在非线上环境使用，一般用于UT测试。
      另外一种方式是：
ConfigManagerLoader.getConfigManager().setLocalStringValue("http://service.dianping.com/com.dianping.pigeon.demo.EchoService", "192.168.0.1:4040");

### 如何定义自己的拦截器

pigeon在客户端调用和服务端调用都提供了拦截器机制，方便用户可以获取到调用参数和返回结果。
注意：请不要在拦截器当中写消耗性能的代码，因为拦截器中的代码都是同步调用，如果执行太慢会影响服务调用的执行时间，用户如果想在拦截器中实现复杂逻辑，请自行进行异步处理。
在客户端可以实现自己的拦截器：

		import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
		import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
		import com.dianping.pigeon.remoting.invoker.process.InvokerProcessInterceptor;
		public class MyInvokerProcessInterceptor implements InvokerProcessInterceptor {
		@Override
		public void preInvoke(InvocationRequest invocationRequest) {
		System.out.println("preInvoke:" + invocationRequest);
		}
		@Override
		public void postInvoke(InvocationRequest invocationRequest, InvocationResponse invocationResponse) {
		System.out.println("postInvoke:" + invocationResponse);
		}
		}
		
在系统初始化时注册到pigeon中：

		InvokerProcessInterceptorFactory.registerInterceptor(new MyInvokerProcessInterceptor());

同样的，在服务端也可以定义类似的拦截器：

		import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
		import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
		import com.dianping.pigeon.remoting.provider.process.ProviderProcessInterceptor;
		public class MyProviderProcessInterceptor implements ProviderProcessInterceptor {
		@Override
		public void preInvoke(InvocationRequest invocationRequest) {
		System.out.println("preInvoke:" + invocationRequest);
		}
		@Override
		public void postInvoke(InvocationRequest invocationRequest, InvocationResponse invocationResponse) {
		System.out.println("postInvoke:" + invocationResponse);
		}
		}
		
在系统初始化时注册到pigeon中：

		ProviderProcessInterceptorFactory.registerInterceptor(new MyProviderProcessInterceptor());


### 如何关闭自动注册

强烈建议不要关闭自动注册，如果特殊场合比如某些服务端需要自己做预热处理后再注册服务，可能需要关闭自动注册功能：
1、在应用的classpath下放config/pigeon.properties文件（该文件的配置是所有环境都生效，包括关闭线上自动注册，请谨慎使用，如果是只设置某个环境，也可以是pigeon_dev.properties/pigeon_alpha.properties/pigeon_qa.properties/pigeon_prelease.properties/pigeon_product.properties），内容如下:
pigeon.autoregister.enable=false
这个配置也可以放在绝对路径/data/webapps/config/pigeon/pigeon.properties文件里
如果是关闭整个应用所有机器的自动注册，可以在lion对应项目配置里加上以下配置，如shop-server这个应用：
shop-server.pigeon.autoregister.enable配置为false

2、预热完了之后，再调pigeon的api完成服务发布：
ServiceFactory.online();
建议sleep一段时间再调这个方法
如果没有调用这个接口，需要自行修改lion服务配置

### 服务端如何获取客户端信息

可通过(String) ContextUtils.getLocalContext("CLIENT_IP")拿到上一级调用客户端的ip地址
可通过(String) ContextUtils.getLocalContext("CLIENT_APP")拿到上一级调用客户端的appname

可通过ContextUtils.getGlobalContext("SOURCE_IP")拿到请求最前端发起者的ip地址
可通过ContextUtils.getGlobalContext("SOURCE_APP")拿到请求最前端发起者的appname

### 如何自定义loadbalance

一般情况下使用pigeon提供的random/roundRobin/weightedAutoaware这几种策略就足够了，如果需要自己实现负载均衡策略，可以在客户端的配置里添加loadBalanceClass属性，这个class必须实现com.dianping.pigeon.remoting.invoker.route.balance.LoadBalance接口，一般可以继承pigeon提供的AbstractLoadBalance抽象类或pigeon目前已有的loadbalance类。
	
		<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean"
		init-method="init">
			<property name="url"
			value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
			<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
			<property name="callType" value="sync" />
			<property name="timeout" value="1000" />
			<property name="loadBalanceClass"
			value="com.dianping.pigeon.demo.loadbalance.MyLoadbalance" />
		</bean>

MyLoadbalance.java

		public class MyLoadbalance extends RoundRobinLoadBalance {
		@Override
		protected Client doSelect(List<Client> clients, InvocationRequest request, int[] weights) {
		if ("http://service.dianping.com/com.dianping.pigeon.demo.EchoService".equals(request.getServiceName())
		&& "echo".equals(request.getMethodName())) {
		if (request.getParameters().length > 0) {
		Object p0 = request.getParameters()[0];
		if (p0 != null) {
		return clients.get(Math.abs(p0.hashCode() % clients.size()));
		}
		}
		}
		return super.doSelect(clients, request, weights);
		}
		}


### 如何控制cat上客户端超时异常的次数

pigeon可以设置客户端发生超时异常时在cat上控制异常记录的次数，可以在lion对应项目配置里加上以下配置，如xxx这个应用（需要保证classes/META-INF/app.properties里的app.name=xxx，这里的xxx必须与lion项目名称保持一致）：

xxx.pigeon.invoker.log.timeout.period.apps配置为shop-server:0,data-server:100

配置内容里，可以配置多个目标服务app的日志打印间隔，以逗号分隔，目标app也必须是点评统一标准应用名，如果某个目标服务app未配置则这个app的超时异常都会记录
每个app后边的数字，默认为0代表每个超时异常都会记录，如果配置为10000则任何超时异常都不会记录到cat，如果为1代表记录一半，如果为100代表每100个超时异常记录一次，数字越大记录的异常越少

### 记录服务端每个请求的详细信息

pigeon可以设在服务端记录客户端发过来的每个请求的详细信息，需要在lion相应项目里配置：
xxx.pigeon.provider.accesslog.enable为true，配置好了之后pigeon会将日志记录在本地以下位置：
/data/applogs/pigeon/pigeon-access.log
每个请求记录的日志内容为：
 应用名称+ "@" + 来源ip+ "@" + 请求对象内容（包含请求参数值等）+ "@" + 时间区间消耗
 
### 记录服务端业务异常详细日志

pigeon在服务端默认不会记录业务方法抛出的异常详细信息，如果需要记录这类业务异常，需要在lion相应项目里配置：
xxx.pigeon.provider.logserviceexception为true
xxx是应用的app.name，需要与lion项目名称保持一致


###	获取服务注册信息


使用pigeon客户端接口：

com.dianping.pigeon.governor.service.RegistrationInfoService 

用法:

		RegistrationInfoService registrationInfoService = ServiceFactory.getService(RegistrationInfoService.class);
		String app = registrationInfoService.getAppOfService("com.dianping.demo.service.XXXService");

依赖：

		<groupId>com.dianping</groupId>
		<artifactId>pigeon-governor-api</artifactId>
		<version>2.6.0-SNAPSHOT</version>


接口说明：

		package com.dianping.pigeon.governor.service;
		
		import java.util.List;
		
		import com.dianping.pigeon.registry.exception.RegistryException;
		
		/**
		 * pigeon注册信息服务
		 * @author xiangwu
		 *
		 */
		public interface RegistrationInfoService {
		
		/**
		* 获取服务的应用名称
		* @param url 服务名称，标示一个服务的url
		* @param group 泳道名称，没有填null
		* @return 应用名称
		* @throws RegistryException
		*/
		String getAppOfService(String url, String group) throws RegistryException;
		
		/**
		* 获取服务的应用名称
		* @param url 服务名称，标示一个服务的url
		* @return 应用名称
		* @throws RegistryException
		*/
		String getAppOfService(String url) throws RegistryException;
		
		/**
		* 获取服务地址的权重
		* @param address 服务地址，格式ip:port
		* @return 权重
		* @throws RegistryException
		*/
		String getWeightOfAddress(String address) throws RegistryException;
		
		/**
		* 获取服务地址的应用名称
		* @param address 服务地址，格式ip:port
		* @return 应用名称
		* @throws RegistryException
		*/
		String getAppOfAddress(String address) throws RegistryException;
		
		/**
		* 获取服务的地址列表
		* @param url 服务名称，标示一个服务的url
		* @param group 泳道，没有填null
		* @return 逗号分隔的地址列表，地址格式ip:port
		* @throws RegistryException
		*/
		List<String> getAddressListOfService(String url, String group) throws RegistryException;
		
		/**
		* 获取服务的地址列表
		* @param url 服务名称，标示一个服务的url
		* @return 逗号分隔的地址列表，地址格式ip:port
		* @throws RegistryException
		*/
		List<String> getAddressListOfService(String url) throws RegistryException;
		}


### 泳道
泳道用于机器级别的隔离，泳道配置在机器的/data/webapps/appenv里，例如：
deployenv=alpha
zkserver=alpha.lion.dp:2182
swimlane=tg

swimlane代表tg这个泳道，对于pigeon来说，如果一个service的机器定义了swimlane为tg，那么这个机器只能是客户端同样为tg泳道的机器能够调用
对于客户端来说，假设配置了泳道为tg，那么这个客户端机器调用远程服务时，会优先选择服务端泳道配置同样为tg的机器，如果tg泳道的机器不可用或不存在，才会调用其他未配置泳道的机器


