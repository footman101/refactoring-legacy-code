package cn.xpbootcamp.legacy_code;

import cn.xpbootcamp.legacy_code.enums.STATUS;
import cn.xpbootcamp.legacy_code.service.WalletService;
import cn.xpbootcamp.legacy_code.service.WalletServiceImpl;
import cn.xpbootcamp.legacy_code.utils.IdGenerator;
import cn.xpbootcamp.legacy_code.utils.RedisDistributedLock;

import javax.transaction.InvalidTransactionException;

public class WalletTransaction {
    private String id;
    private Long buyerId;
    private Long sellerId;
    private Long createdTimestamp;
    private Double amount;
    private STATUS status;

    public static final long EXPIRE_DURATION = 20 * 24 * 60 * 60 * 1000;


    public WalletTransaction(String preAssignedId, Long buyerId, Long sellerId, Double amount) {
        this.id = preAssignedIdToId(preAssignedId);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.status = STATUS.TO_BE_EXECUTED;
        this.createdTimestamp = System.currentTimeMillis();
    }

    private String preAssignedIdToId(String preAssignedId) {
        String result;
        if (preAssignedId != null && !preAssignedId.isEmpty()) {
            result = preAssignedId;
        } else {
            result = new IdGenerator().generateTransactionId();
        }
        if (!result.startsWith("t_")) {
            result = "t_" + preAssignedId;
        }

        return result;
    }


    public boolean execute() throws InvalidTransactionException {
        if (buyerId == null || (sellerId == null || amount < 0.0)) {
            throw new InvalidTransactionException("This is an invalid transaction");
        }

        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - createdTimestamp > EXPIRE_DURATION) {
            this.status = STATUS.EXPIRED;
            return false;
        }

        if (status == STATUS.EXECUTED) return true;
        boolean isLocked = false;
        try {
            isLocked = RedisDistributedLock.getSingletonInstance().lock(id);
            if (!isLocked) {
                return false;
            }

            if (status == STATUS.EXECUTED) return true; // double check

            if (new WalletServiceImpl().moveMoney(id, buyerId, sellerId, amount) != null) {
                this.status = STATUS.EXECUTED;
                return true;
            } else {
                this.status = STATUS.FAILED;
                return false;
            }
        } finally {
            if (isLocked) {
                RedisDistributedLock.getSingletonInstance().unlock(id);
            }
        }
    }

}