package com.dopashift.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.dopashift.data.local.entity.LocalEfficiencyScore;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class EfficiencyScoreDao_Impl implements EfficiencyScoreDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalEfficiencyScore> __insertionAdapterOfLocalEfficiencyScore;

  public EfficiencyScoreDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalEfficiencyScore = new EntityInsertionAdapter<LocalEfficiencyScore>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `efficiency_scores` (`id`,`userId`,`scoreDate`,`productiveSeconds`,`totalTrackedSeconds`,`scorePercent`,`computedAt`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalEfficiencyScore entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getUserId());
        statement.bindString(3, entity.getScoreDate());
        statement.bindLong(4, entity.getProductiveSeconds());
        statement.bindLong(5, entity.getTotalTrackedSeconds());
        if (entity.getScorePercent() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getScorePercent());
        }
        statement.bindLong(7, entity.getComputedAt());
      }
    };
  }

  @Override
  public Object upsert(final LocalEfficiencyScore score,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalEfficiencyScore.insert(score);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object findByUserIdAndDateRange(final String userId, final String from, final String to,
      final Continuation<? super List<LocalEfficiencyScore>> $completion) {
    final String _sql = "SELECT * FROM efficiency_scores WHERE userId = ? AND scoreDate BETWEEN ? AND ? ORDER BY scoreDate";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    _argIndex = 2;
    _statement.bindString(_argIndex, from);
    _argIndex = 3;
    _statement.bindString(_argIndex, to);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalEfficiencyScore>>() {
      @Override
      @NonNull
      public List<LocalEfficiencyScore> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfScoreDate = CursorUtil.getColumnIndexOrThrow(_cursor, "scoreDate");
          final int _cursorIndexOfProductiveSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "productiveSeconds");
          final int _cursorIndexOfTotalTrackedSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "totalTrackedSeconds");
          final int _cursorIndexOfScorePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "scorePercent");
          final int _cursorIndexOfComputedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "computedAt");
          final List<LocalEfficiencyScore> _result = new ArrayList<LocalEfficiencyScore>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalEfficiencyScore _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpScoreDate;
            _tmpScoreDate = _cursor.getString(_cursorIndexOfScoreDate);
            final long _tmpProductiveSeconds;
            _tmpProductiveSeconds = _cursor.getLong(_cursorIndexOfProductiveSeconds);
            final long _tmpTotalTrackedSeconds;
            _tmpTotalTrackedSeconds = _cursor.getLong(_cursorIndexOfTotalTrackedSeconds);
            final Integer _tmpScorePercent;
            if (_cursor.isNull(_cursorIndexOfScorePercent)) {
              _tmpScorePercent = null;
            } else {
              _tmpScorePercent = _cursor.getInt(_cursorIndexOfScorePercent);
            }
            final long _tmpComputedAt;
            _tmpComputedAt = _cursor.getLong(_cursorIndexOfComputedAt);
            _item = new LocalEfficiencyScore(_tmpId,_tmpUserId,_tmpScoreDate,_tmpProductiveSeconds,_tmpTotalTrackedSeconds,_tmpScorePercent,_tmpComputedAt);
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
  public Flow<List<LocalEfficiencyScore>> observeByUserIdAndDateRange(final String userId,
      final String from, final String to) {
    final String _sql = "SELECT * FROM efficiency_scores WHERE userId = ? AND scoreDate BETWEEN ? AND ? ORDER BY scoreDate";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    _argIndex = 2;
    _statement.bindString(_argIndex, from);
    _argIndex = 3;
    _statement.bindString(_argIndex, to);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"efficiency_scores"}, new Callable<List<LocalEfficiencyScore>>() {
      @Override
      @NonNull
      public List<LocalEfficiencyScore> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfScoreDate = CursorUtil.getColumnIndexOrThrow(_cursor, "scoreDate");
          final int _cursorIndexOfProductiveSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "productiveSeconds");
          final int _cursorIndexOfTotalTrackedSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "totalTrackedSeconds");
          final int _cursorIndexOfScorePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "scorePercent");
          final int _cursorIndexOfComputedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "computedAt");
          final List<LocalEfficiencyScore> _result = new ArrayList<LocalEfficiencyScore>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalEfficiencyScore _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpScoreDate;
            _tmpScoreDate = _cursor.getString(_cursorIndexOfScoreDate);
            final long _tmpProductiveSeconds;
            _tmpProductiveSeconds = _cursor.getLong(_cursorIndexOfProductiveSeconds);
            final long _tmpTotalTrackedSeconds;
            _tmpTotalTrackedSeconds = _cursor.getLong(_cursorIndexOfTotalTrackedSeconds);
            final Integer _tmpScorePercent;
            if (_cursor.isNull(_cursorIndexOfScorePercent)) {
              _tmpScorePercent = null;
            } else {
              _tmpScorePercent = _cursor.getInt(_cursorIndexOfScorePercent);
            }
            final long _tmpComputedAt;
            _tmpComputedAt = _cursor.getLong(_cursorIndexOfComputedAt);
            _item = new LocalEfficiencyScore(_tmpId,_tmpUserId,_tmpScoreDate,_tmpProductiveSeconds,_tmpTotalTrackedSeconds,_tmpScorePercent,_tmpComputedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
