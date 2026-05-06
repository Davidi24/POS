package pos.pos.exception.tables;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class TableCategoryNotFoundException extends AuthException {

    public TableCategoryNotFoundException() {
        super("Table category not found", HttpStatus.NOT_FOUND);
    }
}
