---
title: "Logging raw SQL in MySQL JDBC Driver"
date: 2019-07-28T16:25:39+02:00
---

I recently wanted to see exactly what SQL statements were being executed by
Hibernate/JPA. In this post, I show a simple solution using built-in
capabilities in the MySQL JDBC driver.

<!--more-->

## What I wanted to accomplish

* Log *all* SQL statements being performed. Preferably in a format that could be
  copy/pasted into an SQL console.
* Preferably, the logging would be implemented by myself using some sort of
  event handler, since I wanted control over the logging logic in order to add
  [structured logging](https://www.elastic.co/blog/structured-logging-filebeat).
* Pretty-printing is nice, but not a requirement.

After some experimenting, this ruled out

* The hibernate internal logger. I was unable to easily modify the format of
  the log messages, and I could only get it to log prepared statements with
  `?` variable placeholders.
* DataSource wrappers like https://github.com/ttddyy/datasource-proxy. Again I
  could not get it to log the "raw" SQL statements without `?` placeholders.

Luckily, I found out that the MySQL JDBC driver has support for registering a
`ProfilerEventHandler` that allows you to log all SQL queries being performed.

```java
@Bean // Example of integration with spring boot
public DataSource mysqlDataSource() throws SQLException {
    MysqlDataSource dataSource = new MysqlDataSource();
    dataSource.setServerName("127.0.0.1");
    // ... more lines here setting user, password, etc.
    dataSource.setProfileSQL(true);
    dataSource.setProfilerEventHandler(
        MySQLQueryLogger.class.getName()
    );
    return dataSource;
}
```

```java
public class MySQLQueryLogger extends LoggingProfilerEventHandler {
    private final Logger logger = LoggerFactory.getLogger(MySQLQueryLogger.class);

    @Override
    public void consumeEvent(ProfilerEvent evt) {
        if (evt.getEventType() == ProfilerEvent.TYPE_QUERY) {
            logger.info("{}; /* {}{} */",
                evt.getMessage(),
                evt.getEventDuration(),
                evt.getDurationUnits()
            );
        }
    }
}
```

Using the above, I could now see the following in my logs:

```
Application      : Saving two new customers
MySQLQueryLogger : SET autocommit=1; /* 1ms */
MySQLQueryLogger : SET sql_mode='STRICT_TRANS_TABLES'; /* 1ms */
MySQLQueryLogger : SET autocommit=0; /* 0ms */
MySQLQueryLogger : insert into customers (name) values ('John Doe'); /* 1ms */
MySQLQueryLogger : insert into customers (name) values ('Jane Doe'); /* 1ms */
MySQLQueryLogger : commit; /* 2ms */
MySQLQueryLogger : SET autocommit=1; /* 1ms */
Application      : Saving done
```

For a full example, see source code in https://github.com/remen/spring-boot-mysql-logger
