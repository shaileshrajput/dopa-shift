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
import com.dopashift.data.local.entity.LocalInterceptionRule;
import java.lang.Class;
import java.lang.Exception;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class InterceptionRuleDao_Impl implements InterceptionRuleDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalInterceptionRule> __insertionAdapterOfLocalInterceptionRule;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public InterceptionRuleDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalInterceptionRule = new EntityInsertionAdapter<LocalInterceptionRule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `interception_rules` (`id`,`userId`,`goalId`,`appPackageName`,`siteDomain`,`dailyAllowanceMinutes`,`isActive`,`createdAt`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalInterceptionRule entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getUserId());
        if (entity.getGoalId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getGoalId());
        }
        if (entity.getAppPackageName() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getAppPackageName());
        }
        if (entity.getSiteDomain() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSiteDomain());
        }
        statement.bindLong(6, entity.getDailyAllowanceMinutes());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindLong(8, entity.getCreatedAt());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM interception_rules WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final LocalInterceptionRule rule,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalInterceptionRule.insert(rule);
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
  public Object findActiveByPackageName(final String packageName,
      final Continuation<? super LocalInterceptionRule> $completion) {
    final String _sql = "SELECT * FROM interception_rules WHERE appPackageName = ? AND isActive = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packageName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalInterceptionRule>() {
      @Override
      @Nullable
      public LocalInterceptionRule call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfGoalId = CursorUtil.getColumnIndexOrThrow(_cursor, "goalId");
          final int _cursorIndexOfAppPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "appPackageName");
          final int _cursorIndexOfSiteDomain = CursorUtil.getColumnIndexOrThrow(_cursor, "siteDomain");
          final int _cursorIndexOfDailyAllowanceMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "dailyAllowanceMinutes");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final LocalInterceptionRule _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpGoalId;
            if (_cursor.isNull(_cursorIndexOfGoalId)) {
              _tmpGoalId = null;
            } else {
              _tmpGoalId = _cursor.getString(_cursorIndexOfGoalId);
            }
            final String _tmpAppPackageName;
            if (_cursor.isNull(_cursorIndexOfAppPackageName)) {
              _tmpAppPackageName = null;
            } else {
              _tmpAppPackageName = _cursor.getString(_cursorIndexOfAppPackageName);
            }
            final String _tmpSiteDomain;
            if (_cursor.isNull(_cursorIndexOfSiteDomain)) {
              _tmpSiteDomain = null;
            } else {
              _tmpSiteDomain = _cursor.getString(_cursorIndexOfSiteDomain);
            }
            final int _tmpDailyAllowanceMinutes;
            _tmpDailyAllowanceMinutes = _cursor.getInt(_cursorIndexOfDailyAllowanceMinutes);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new LocalInterceptionRule(_tmpId,_tmpUserId,_tmpGoalId,_tmpAppPackageName,_tmpSiteDomain,_tmpDailyAllowanceMinutes,_tmpIsActive,_tmpCreatedAt);
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
  public Object findActiveByUserId(final String userId,
      final Continuation<? super List<LocalInterceptionRule>> $completion) {
    final String _sql = "SELECT * FROM interception_rules WHERE userId = ? AND isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalInterceptionRule>>() {
      @Override
      @NonNull
      public List<LocalInterceptionRule> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfGoalId = CursorUtil.getColumnIndexOrThrow(_cursor, "goalId");
          final int _cursorIndexOfAppPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "appPackageName");
          final int _cursorIndexOfSiteDomain = CursorUtil.getColumnIndexOrThrow(_cursor, "siteDomain");
          final int _cursorIndexOfDailyAllowanceMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "dailyAllowanceMinutes");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<LocalInterceptionRule> _result = new ArrayList<LocalInterceptionRule>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalInterceptionRule _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpGoalId;
            if (_cursor.isNull(_cursorIndexOfGoalId)) {
              _tmpGoalId = null;
            } else {
              _tmpGoalId = _cursor.getString(_cursorIndexOfGoalId);
            }
            final String _tmpAppPackageName;
            if (_cursor.isNull(_cursorIndexOfAppPackageName)) {
              _tmpAppPackageName = null;
            } else {
              _tmpAppPackageName = _cursor.getString(_cursorIndexOfAppPackageName);
            }
            final String _tmpSiteDomain;
            if (_cursor.isNull(_cursorIndexOfSiteDomain)) {
              _tmpSiteDomain = null;
            } else {
              _tmpSiteDomain = _cursor.getString(_cursorIndexOfSiteDomain);
            }
            final int _tmpDailyAllowanceMinutes;
            _tmpDailyAllowanceMinutes = _cursor.getInt(_cursorIndexOfDailyAllowanceMinutes);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new LocalInterceptionRule(_tmpId,_tmpUserId,_tmpGoalId,_tmpAppPackageName,_tmpSiteDomain,_tmpDailyAllowanceMinutes,_tmpIsActive,_tmpCreatedAt);
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
  public Object findById(final String id,
      final Continuation<? super LocalInterceptionRule> $completion) {
    final String _sql = "SELECT * FROM interception_rules WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalInterceptionRule>() {
      @Override
      @Nullable
      public LocalInterceptionRule call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfGoalId = CursorUtil.getColumnIndexOrThrow(_cursor, "goalId");
          final int _cursorIndexOfAppPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "appPackageName");
          final int _cursorIndexOfSiteDomain = CursorUtil.getColumnIndexOrThrow(_cursor, "siteDomain");
          final int _cursorIndexOfDailyAllowanceMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "dailyAllowanceMinutes");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final LocalInterceptionRule _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpGoalId;
            if (_cursor.isNull(_cursorIndexOfGoalId)) {
              _tmpGoalId = null;
            } else {
              _tmpGoalId = _cursor.getString(_cursorIndexOfGoalId);
            }
            final String _tmpAppPackageName;
            if (_cursor.isNull(_cursorIndexOfAppPackageName)) {
              _tmpAppPackageName = null;
            } else {
              _tmpAppPackageName = _cursor.getString(_cursorIndexOfAppPackageName);
            }
            final String _tmpSiteDomain;
            if (_cursor.isNull(_cursorIndexOfSiteDomain)) {
              _tmpSiteDomain = null;
            } else {
              _tmpSiteDomain = _cursor.getString(_cursorIndexOfSiteDomain);
            }
            final int _tmpDailyAllowanceMinutes;
            _tmpDailyAllowanceMinutes = _cursor.getInt(_cursorIndexOfDailyAllowanceMinutes);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new LocalInterceptionRule(_tmpId,_tmpUserId,_tmpGoalId,_tmpAppPackageName,_tmpSiteDomain,_tmpDailyAllowanceMinutes,_tmpIsActive,_tmpCreatedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
