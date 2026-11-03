package demo.inspection.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerApplication {

    /**
     * 启动 Spring Boot HTTP 与定时任务容器，无返回值，作为 server 的进程入口。
     *
     * @param args 传递给 Spring Boot 的命令行参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
