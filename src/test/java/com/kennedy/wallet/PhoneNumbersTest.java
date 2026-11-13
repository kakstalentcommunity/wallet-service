package com.kennedy.wallet;
import com.kennedy.wallet.service.PhoneNumbers;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class PhoneNumbersTest {@Test void normalizesLocalAndInternationalForms(){assertEquals("+254712345678",PhoneNumbers.normalize("0712345678"));assertEquals("+254112345678",PhoneNumbers.normalize("+254112345678"));assertEquals("+254712345678",PhoneNumbers.normalize("254712345678"));}@Test void rejectsInvalidNumbers(){assertThrows(IllegalArgumentException.class,()->PhoneNumbers.normalize("123"));}}
