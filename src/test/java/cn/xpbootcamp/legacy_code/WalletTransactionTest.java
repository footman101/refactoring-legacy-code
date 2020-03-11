package cn.xpbootcamp.legacy_code;

import cn.xpbootcamp.legacy_code.entity.User;
import cn.xpbootcamp.legacy_code.repository.UserRepository;
import cn.xpbootcamp.legacy_code.repository.UserRepositoryImpl;
import cn.xpbootcamp.legacy_code.service.WalletServiceImpl;
import cn.xpbootcamp.legacy_code.utils.RedisDistributedLock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.FieldSetter;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.transaction.InvalidTransactionException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;


@RunWith(PowerMockRunner.class)
@PrepareForTest({RedisDistributedLock.class, WalletServiceImpl.class})
public class WalletTransactionTest {
    private UserRepositoryImpl userRepository;
    private RedisDistributedLock redisDistributedLock;
    private User buyer = new User();
    private User seller = new User();

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(RedisDistributedLock.class);
        redisDistributedLock = PowerMockito.mock(RedisDistributedLock.class);
        PowerMockito.when(RedisDistributedLock.getSingletonInstance()).thenReturn(redisDistributedLock);

        userRepository = PowerMockito.mock(UserRepositoryImpl.class);
        PowerMockito.whenNew(UserRepositoryImpl.class).withAnyArguments().thenReturn(userRepository);
    }

    public void mockValidBuyerAndSeller() {
        buyer.setBalance(1000);
        PowerMockito.when(userRepository.find(1000L)).thenReturn(buyer);
        seller.setBalance(1000);
        PowerMockito.when(userRepository.find(2000L)).thenReturn(seller);
    }

    public void mockInValidBuyerAndSeller() {
        buyer.setBalance(10);
        PowerMockito.when(userRepository.find(1000L)).thenReturn(buyer);
        seller.setBalance(1000);
        PowerMockito.when(userRepository.find(2000L)).thenReturn(seller);
    }

    @Test
    public void should_return_true_when_buyer_have_enough_money() throws InvalidTransactionException {
        PowerMockito.when(redisDistributedLock.lock(anyString())).thenReturn(true);
        mockValidBuyerAndSeller();
        WalletTransaction walletTransaction = new WalletTransaction("t_1", 1000L, 2000L, 100.0);
        assertTrue(walletTransaction.execute());
        assertEquals(buyer.getBalance(), 900, 0.0001);
        assertEquals(seller.getBalance(), 1100, 0.0001);
    }

    @Test
    public void should_return_false_when_buyer_do_not_have_enough_money() throws InvalidTransactionException {
        PowerMockito.when(redisDistributedLock.lock(anyString())).thenReturn(true);
        mockInValidBuyerAndSeller();
        WalletTransaction walletTransaction = new WalletTransaction("t_1", 1000L, 2000L, 100.0);
        assertFalse(walletTransaction.execute());
        assertEquals(buyer.getBalance(), 10, 0.0001);
        assertEquals(seller.getBalance(), 1000, 0.0001);
    }

    @Test
    public void should_return_false_when_get_lock_failed() throws InvalidTransactionException {
        PowerMockito.when(redisDistributedLock.lock(anyString())).thenReturn(false);
        mockValidBuyerAndSeller();
        PowerMockito.when(userRepository.find(2000L)).thenReturn(seller);
        WalletTransaction walletTransaction = new WalletTransaction("t_1", 1000L, 2000L, 100.0);
        assertFalse(walletTransaction.execute());
        assertEquals(buyer.getBalance(), 1000, 0.0001);
        assertEquals(seller.getBalance(), 1000, 0.0001);
    }

    @Test
    public void should_return_false_when_transaction_expired() throws InvalidTransactionException, NoSuchFieldException {
        PowerMockito.when(redisDistributedLock.lock(anyString())).thenReturn(true);
        mockValidBuyerAndSeller();
        WalletTransaction walletTransaction = new WalletTransaction("t_1", 1000L, 2000L, 100.0);
        FieldSetter.setField(walletTransaction, WalletTransaction.class.getDeclaredField("createdTimestamp"), 0L);
        assertFalse(walletTransaction.execute());
        assertEquals(buyer.getBalance(), 1000, 0.0001);
        assertEquals(seller.getBalance(), 1000, 0.0001);
    }
}
