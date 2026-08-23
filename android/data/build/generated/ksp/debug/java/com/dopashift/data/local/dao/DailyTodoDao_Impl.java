package com.dopashift.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.dopashift.data.local.entity.LocalDailyTodo;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DailyTodoDao_Impl implements DailyTodoDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalDailyTodo> __insertionAdapterOfLocalDailyTodo;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public DailyTodoDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalDailyTodo = new EntityInsertionAdapter<LocalDailyTodo>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `daily_todo_items` (`id`,`userId`,`text`,`dueDateTime`,`isCompleted`,`dayDate`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalDailyTodo entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getUserId());
        statement.bindString(3, entity.getText());
        if (entity.getDueDateTime() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getDueDateTime());
        }
        final int _tmp = entity.isCompleted() ? 1 : 0;
        statement.bindLong(5, _tmp);
        statement.bindString(6, entity.getDayDate());
        statement.bindLong(7, entity.getCreatedAt());
        statement.bindLong(8, entity.getUpdatedAt());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM daily_todo_items WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final LocalDailyTodo item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalDailyTodo.insert(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object findById(final String id, final Continuation<? super LocalDailyTodo> $completion) {
    final String _sql = "SELECT * FROM daily_todo_items WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalDailyTodo>() {
      @Override
      @Nullable
      public LocalDailyTodo call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfDueDateTime = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDateTime");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfDayDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dayDate");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final LocalDailyTodo _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final Long _tmpDueDateTime;
            if (_cursor.isNull(_cursorIndexOfDueDateTime)) {
              _tmpDueDateTime = null;
            } else {
              _tmpDueDateTime = _cursor.getLong(_cursorIndexOfDueDateTime);
            }
            final boolean _tmpIsCompleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp != 0;
            final String _tmpDayDate;
            _tmpDayDate = _cursor.getString(_cursorIndexOfDayDate);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new LocalDailyTodo(_tmpId,_tmpUserId,_tmpText,_tmpDueDateTime,_tmpIsCompleted,_tmpDayDate,_tmpCreatedAt,_tmpUpdatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findByUserIdAndDate(final String userId, final String date,
      final Continuation<? super List<LocalDailyTodo>> $completion) {
    final String _sql = "SELECT * FROM daily_todo_items WHERE userId = ? AND dayDate = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    _argIndex = 2;
    _statement.bindString(_argIndex, date);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalDailyTodo>>() {
      @Override
      @NonNull
      public List<LocalDailyTodo> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfDueDateTime = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDateTime");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfDayDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dayDate");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalDailyTodo> _result = new ArrayList<LocalDailyTodo>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalDailyTodo _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final Long _tmpDueDateTime;
            if (_cursor.isNull(_cursorIndexOfDueDateTime)) {
              _tmpDueDateTime = null;
            } else {
              _tmpDueDateTime = _cursor.getLong(_cursorIndexOfDueDateTime);
            }
            final boolean _tmpIsCompleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp != 0;
            final String _tmpDayDate;
            _tmpDayDate = _cursor.getString(_cursorIndexOfDayDate);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalDailyTodo(_tmpId,_tmpUserId,_tmpText,_tmpDueDateTime,_tmpIsCompleted,_tmpDayDate,_tmpCreatedAt,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LocalDailyTodo>> observeByUserIdAndDate(final String userId, final String date) {
    final String _sql = "SELECT * FROM daily_todo_items WHERE userId = ? AND dayDate = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    _argIndex = 2;
    _statement.bindString(_argIndex, date);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"daily_todo_items"}, new Callable<List<LocalDailyTodo>>() {
      @Override
      @NonNull
      public List<LocalDailyTodo> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfDueDateTime = CursorUtil.getColumnIndexOrThrow(_cursor, "dueDateTime");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfDayDate = CursorUtil.getColumnIndexOrThrow(_cursor, "dayDate");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalDailyTodo> _result = new ArrayList<LocalDailyTodo>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalDailyTodo _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final Long _tmpDueDateTime;
            if (_cursor.isNull(_cursorIndexOfDueDateTime)) {
              _tmpDueDateTime = null;
            } else {
              _tmpDueDateTime = _cursor.getLong(_cursorIndexOfDueDateTime);
            }
            final boolean _tmpIsCompleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp != 0;
            final String _tmpDayDate;
            _tmpDayDate = _cursor.getString(_cursorIndexOfDayDate);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalDailyTodo(_tmpId,_tmpUserId,_tmpText,_tmpDueDateTime,_tmpIsCompleted,_tmpDayDate,_tmpCreatedAt,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object countByUserIdAndDate(final String userId, final String date,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM daily_todo_items WHERE userId = ? AND dayDate = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    _argIndex = 2;
    _statement.bindString(_argIndex, date);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
