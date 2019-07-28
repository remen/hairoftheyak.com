package hairoftheyak;

import com.mysql.cj.jdbc.MysqlDataSource;
import hairoftheyak.entities.Customer;
import hairoftheyak.entities.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;

@SpringBootApplication
public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class, args);

        CustomerRepository customerRepository = context.getBean(CustomerRepository.class);

        LOGGER.info("Deleting all customers");
        customerRepository.deleteAll();
        LOGGER.info("Deleting done");

        LOGGER.info("Saving two new customes");
        customerRepository.saveAll(Arrays.asList(
            new Customer("John Doe"),
            new Customer("Jane Doe")
        ));
        LOGGER.info("Saving done");
    }

    @Bean
    DataSource dataSource() throws SQLException {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setUser("root");
        dataSource.setPassword("");
        dataSource.setDatabaseName("test");
        dataSource.setCreateDatabaseIfNotExist(true);
        dataSource.setProfileSQL(true);
        dataSource.setProfilerEventHandler(
            MySQLQueryLogger.class.getCanonicalName()
        );
        return dataSource;
    }
}
