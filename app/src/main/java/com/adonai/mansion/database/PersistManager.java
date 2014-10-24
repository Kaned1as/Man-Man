package com.adonai.mansion.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Created by adonai on 29.06.14.
 */
public class PersistManager extends OrmLiteSqliteOpenHelper {

    private static final String TAG = PersistManager.class.getSimpleName();

    //имя файла базы данных который будет храниться в /data/data/APPNAME/DATABASE_NAME
    private static final String DATABASE_NAME ="mansion.db";

    //с каждым увеличением версии, при нахождении в устройстве БД с предыдущей версией будет выполнен метод onUpgrade();
    private static final int DATABASE_VERSION = 1;

    //ссылки на DAO соответсвующие сущностям, хранимым в БД
    /*private EntityDao<Account> accountDao = null;
    private EntityDao<Budget> budgetDao = null;
    private EntityDao<BudgetItem> budgetItemDao = null;
    private EntityDao<Category> categoryDao = null;
    private Dao<Currency, String> currencyDao = null;
    private EntityDao<Operation> operationDao = null;*/
    private final Context mContext;

    public PersistManager(Context context){
        super(context,DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
    }

    //Выполняется, когда файл с БД не найден на устройстве
    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource connectionSource) {
        /*try {
            TableUtils.createTable(connectionSource, Account.class);
            TableUtils.createTable(connectionSource, Budget.class);
            TableUtils.createTable(connectionSource, BudgetItem.class);
            TableUtils.createTable(connectionSource, Category.class);
            TableUtils.createTable(connectionSource, Currency.class);
            TableUtils.createTable(connectionSource, Operation.class);
        } catch (SQLException e) {
            Log.e(TAG, "error creating DB " + DATABASE_NAME);
            throw new RuntimeException(e);
        }*/
    }

    //Выполняется, когда БД имеет версию отличную от текущей
    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVer, int newVer) {

    }

    //выполняется при закрытии приложения
    @Override
    public void close() {
        super.close();
    }
}
