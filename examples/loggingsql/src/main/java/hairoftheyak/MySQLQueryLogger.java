package hairoftheyak;

import com.mysql.cj.log.Log;
import com.mysql.cj.log.ProfilerEvent;
import com.mysql.cj.log.ProfilerEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySQLQueryLogger implements ProfilerEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(MySQLQueryLogger.class);

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

    @Override
    public void init(Log log) {
    }

    @Override
    public void destroy() {
    }

}
