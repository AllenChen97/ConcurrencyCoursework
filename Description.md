# First of all
## Package and class explanation
- Important class in package of **uk.ncl.CSC8016.JinzhaoChen.Implementations**
  - BankFacade was extended by BankFacadeImpl.java
  - TransactionCommands was implemented as an anonymous inner class in openTransaction method in BankFacadeImpl.java
  - A requesting demo was written in MainProgram.java

- Other class
  - All logs would be stored in **uk.ncl.CSC8016.JinzhaoChen.Logs**, while the uncompleted tasks would be serialised in package **UncompletedLog**
  - In **uk.ncl.CSC8016.JinzhaoChen.Utils** 
    - BankLogger.class was used for serialising uncompleted task and print out the operation implementing
    - AtomicBigInteger.class was for generating TransactionID
    - AtomicDoubleBalance.class was used for saving balance of users' account in the map

# Single-Thread Correctness

## 1.  cannot open a transaction if the user does not appear in the initialization map - 10%
- In openTransaction() method of BankFacadeImpl.class, using a decision to check if the user is in the map
- If user does not exist in the map, return empty Option.
```java
    @Override
    public Optional<TransactionCommands> openTransaction(String userId) {
        if(hashMap.containsKey(userId)) {
            return Optional.of(...);
        } else
            return Optional.empty();
    }
```

## 2. The returned totalAmount reflect the amount that was committed - 10%
- The balance changed in each operation would be print out by invoking BankLogger's method in the implementation of TransactionCommands, including the time that finishing a transaction by commit/abort.

## 3. Present the total changes into the account - 15%
- including committing and abort

## 4. No overdraft - 15%
- When commit the withdrawal operation, program would exploit CompareAndSet strategy in a for loop of AtomicDoubleBalance.class
- the overdraft transaction would be marked as failed in Result.class
```java
// if overdraft
if (!beginBalance.incrementAndGet(totalLocalOperations)) {

    // update value and status of transaction fail in result
    result.setBalance(getBalanceInAccount(userId));
    result.setStatus(-1);

    log.overdraft(getBalanceInAccount(userId));
    log.moveLogWhenCompleted();
    return result;
}
else {
    // update value in result
    result.setBalance(getBalanceInAccount(userId));

    log.commitSuccessful(totalLocalOperations, hashMap.get(userId).getValue());
    log.moveLogWhenCompleted();
    return result;
}
```

# Multi-Threaded Correctness

## 1. A single user can open concurrent transactions - 10%
- AtomicDoubleBalance is developed by AtomicReference<Double>, which is used for storing balance of users' account.
- All threads can perceive updated value instantly when committed by another thread. 
- Before committing operations, a compareAndSet method are invoked in AtomicDoubleBalance, avoiding write-write conflict.
```java
public boolean incrementAndGet(Double addvalue) {
    for (; ; ) {
        value = valueHolder.get();
        Double next = value + addvalue;
        if (next < 0) {
            return false;
        }
        if (valueHolder.compareAndSet(value, next)) {
            return true;
        }
    }
}
```

## 2. Multiple users can open concurrent transactions - 10%
- ConcurrentHashMap use the thought of segment locking ensuring multiple can access to the map concurrently, rather than locking the whole map blocking other users when a thread is operating

## 3. No dirty reads allowed - 10%
- Withdraw 0.0 is allowed.
- The operation amount would be accumulated locally until commit successfully.
- Another word, in same account, threads can not withdraw the money paid but uncommitted.
```java
@Override
public boolean payMoneyToAccount(double amount) {
    boolean validation = validationBeforePayAndWithdraw(amount);
    if(!validation) return validation;
    else {
        journal.add(Operation.Pay(amount, journal.size()));
        totalLocalOperations += amount;
        log.operatedLocally(true, amount, getBalanceInAccount(userId));
        return true;
    }
}

@Override
public boolean withdrawMoney(double amount) {
    boolean validation = validationBeforePayAndWithdraw(amount);
    if(!validation) return validation;
    else {
        journal.add(Operation.Withdraw(amount, journal.size()));
        totalLocalOperations -= amount;
        log.operatedLocally(false, amount, getBalanceInAccount(userId));
        return true;
    }
}
```

## 4. Handing aborted transactions - 10%
- The abort transaction would be updated into the map which contained the balance of users, which means all the operation above would be ignored.


# Advanced Features

## 1. Bank server thread - 1%
- Initialised in the constructor of BankFacadeImpl.class by invoking this.bankServer() method

## 2. Exploits Java’s concurrent collections - 1%
- In BankFacadeImpl.class
  - private final ConcurrentHashMap<String, AtomicDoubleBalance> hashMap;
  - CopyOnWriteArrayList<CommitResult> transactionMsg;

## 3. Visually determine the correctness of the operations performed by the threads - 1%
- each operation would invoke methods of BankLogger to print out the result
```java
public void bankServer() {
    requestPool.submit(()->{
        // find if there is any uncompleted task, if yes then redo the operations
        // Loop through and add the results to resultList
        for (Result r : this.initialiser()) {
            addResultList(r);
        }
        // print out the info of threads each second
        for (;;) {
            this.heartBeat();
            TimeUnit.SECONDS.sleep(1);
        }
    });
}
```

## 4. semaphores - 1%

## 5. A thread perceives the updated account balance as soon as any of the remaining concurrent thread is committed - 2%
- implemented by using AtomicReference<Double> to hold the value of balance
```java
private final ConcurrentHashMap<String, AtomicDoubleBalance> hashMap;
```

## 6. Optimistic transaction principle - 2%
- By using atomicReference to hold the balance value in the map, user can operate on multiple devices concurrently.
- The Account would be only locked when updating the value in commit operation, which would not effects other threads to read.


## 7. Usage of monitors or multithreaded producers and consumers - 2%

## 8. Thread pools are used to handle multiple requests from multiple users - 3%
- A thread pool name requestPool was initialised in BankFacadeImpl.class by setting the number of core pool as number of CPU core
- and using LinkedBlockingDeque to store the tasks waiting in queue.

## 9. Any Java library imported via pom.xml ‘not violating the 3rd Submission Requirement - 3%

## 10. In addition to the previous point, bank thread crashes are also tolerated - 4%
- In BankLogger, each operation was stored in a List in Result.class and it would be serialised and written on disk
- When server resume after a crash, the thread of bank server would scan the package of uncompleted task and redo the operation
```java
public ArrayList<Result> initialiser(){
    ArrayList<Result> taskResultArr = new ArrayList<>();

    // 1. read all log file of uncompleted tasks
    File UncompletedLogFolder = new File(UncompletedLogFolderDir);
    File[] files = UncompletedLogFolder.listFiles();

    // 2. loop through and redo the uncompleted tasks
    if (files.length > 0){
        for (File UncompletedLog: files) {
            try{
                // redo the operation and add the result to the Array
                taskResultArr.add(redo(BankLogger.readUncompletedTask(UncompletedLog)));
                // delete the uncompletedTask file
                UncompletedLog.delete();
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    return taskResultArr;
}
private Result redo(Result uncompletedTask){
    // 1. Reopen a transaction for each unfinished task
    TransactionCommands cmds = openTransaction(uncompletedTask.getUserId()).get();

    // 2. Redo the operations
    double totalLocalOperations = Operation.cumulative(uncompletedTask.getOperations());
    cmds.redoOperations(totalLocalOperations);

    // 3. recommit
    return cmds.commit();
}
```
