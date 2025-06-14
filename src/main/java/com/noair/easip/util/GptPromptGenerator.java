package com.noair.easip.util;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class GptPromptGenerator {
    public static final String POST_HOUSE_DEVELOPER_MSG = """
            Respond based on the content of the rental housing announcement PDF file provided by the user. Only answer in JSON format according to the user’s request; do not provide any other explanation or conversation. The keys in the JSON response must be written in English, but the values must be exactly as they appear in Korean in the PDF file.
            
            For rental housing, the higher the deposit, the lower the rent. Therefore, the announcement may provide different rent amounts depending on the deposit ratio. In such cases, treat each as a separate data entry in your response.
            
            The value of livingType must be composed of two parts: the unit type (e.g., “17m(A)”) and the subscription type (e.g., “특별공급”), combined together (e.g., “17m(A) 특별공급”).
            
            Change the following Korean values to the specified English values:
            - "청년": "YOUNG_MAN"
            - "청년(남)": "MALE_YOUNG_MAN"
            - "청년(여)": "FEMALE_YOUNG_MAN"
            - "청년(쉐어)": "SHARE_YOUNG_MAN"
            - "신혼부부", "(예비)신혼부부": "NEWLY_MARRIED_COUPLE"
            - "청년및신혼부부": "BOTH"
            - "전체": "ALL"
            
            
            Change the following Korean keys to the specified English keys:
            - "공급유형": "supplyType"
            - "주거유형": "livingType"
            - "보증금": "deposit"
            - "임대료", "월세": "monthlyRent"
            - "공급호수": "supplyRoomCount"
            
            Values such as deposit and rent amounts must be answered as numbers, not as strings.
            """;

    public static final String POST_HOUSE_USER_MSG = """
            해당 공고문에서 공급유형과 주거유형, 보증금, 임대료별로 각각 공급호수를 리스트로 나열해라. 공급호수가 0이라면 제외해도 된다. 만약 공급유형이 "청년(남)", "청년(여)", "청년(쉐어)", "청년", "(예비)신혼부부", "청년및신혼부부" 중에 없다면 "ALL"로 답변해라.
            
            답변의 예시는 다음과 같다.
            [   {     "supplyType": "YOUNG_MAN",     "livingType": "17.05m²(A) 특별공급",     "deposit": 60000000,     "monthlyRent": 260000,     "supplyRoomCount": 1   },   {     "supplyType": "YOUNG_MAN",     "livingType": "17.05m²(A) 특별공급",     "deposit": 70000000,     "monthlyRent": 160000,     "supplyRoomCount": 1   },   {     "supplyType": "YOUNG_MAN",     "livingType": "17.05m²(A) 특별공급",     "deposit": 80000000,     "monthlyRent": 60000,     "supplyRoomCount": 1   },   {     "supplyType": "YOUNG_MAN",     "livingType": "17.05m²(A) 일반공급",     "deposit": 70000000,     "monthlyRent": 320000,     "supplyRoomCount": 1   },   {     "supplyType": "YOUNG_MAN",     "livingType": "17.05m²(A) 일반공급",     "deposit": 80000000,     "monthlyRent": 220000,     "supplyRoomCount": 1   },   {     "supplyType": "YOUNG_MAN",     "livingType": "17.05m²(A) 일반공급",     "deposit": 90000000,     "monthlyRent": 120000,     "supplyRoomCount": 1   },   {     "supplyType": "NEWLY_MARRIED_COUPLE",     "livingType": "17.46m²(B) 일반공급",     "deposit": 70000000,     "monthlyRent": 340000,     "supplyRoomCount": 1   },   {     "supplyType": "NEWLY_MARRIED_COUPLE",     "livingType": "17.46m²(B) 일반공급",     "deposit": 80000000,     "monthlyRent": 240000,     "supplyRoomCount": 1   },   {     "supplyType": "NEWLY_MARRIED_COUPLE",     "livingType": "17.46m²(B) 일반공급",     "deposit": 90000000,     "monthlyRent": 140000,     "supplyRoomCount": 1   } ]
            """;

    public static final String POST_DEVELOPER_MSG = """
            Respond based on the content of the rental housing announcement PDF file provided by the user. Only answer in JSON format according to the users request; do not provide any other explanation or conversation. The keys in the JSON response must be written in English, but the values must be exactly as they appear in Korean in the PDF file.
            
            특별공급이 아니라 일반공급의 신청자격을 바탕으로 답변해라.
            
            만약 숫자에 단위가 붙어있다면 반드시 계산해서 제거하고 숫자만 표기해라. 예를들어 3803만원은 3803 * 10000이므로 38030000이다. 254백만원은 254 * 1000000이므로 254000000으로 표기한다. 하지만 계산된 숫자는 절대 1000000000을 넘지 않는다.
            
            Change the following Korean keys to the specified English keys:
            - "소득기준 여부": "isIncomeLimited"
            - "1인가구 소득기준": "incomeLimit1Person"
            - "2인가구 소득기준": "incomeLimit2Person"
            - "3인가구 소득기준": "incomeLimit3Person"
            - "4인가구 소득기준": "incomeLimit4Person"
            - "5인가구 소득기준": "incomeLimit5Person"
            - "자동차기준 여부": "isCarPriceLimited"
            - "자동차가액 기준": "carPriceLimit"
            - "자산기준여부": "isAssetLimited"
            - "청년 자산기준": "youngManAssetLimit"
            - "신혼부부 자산기준", "(예비)신혼부부 자산기준": "newlyMarriedCoupleAssetLimit"
            
            Values such as deposit and rent amounts must be answered as numbers, not as strings.
            """;

    public static final String POST_USER_MSG = """
            공고문에서 신청자격 조건을 바탕으로 답변해라.
            
            소득 조건이 존재한다면 1인가구 소득기준, 2인가구 소득기준, 3인가구 소득기준, 4인가구 소득기준, 5인가구 소득기준을 나열하고, 존재하지 않는다면 null. 만약 소득 조건이 '공공주택 입주자 자산기준'을 준용한다면, 1인가구 소득기준=4317797, 2인가구 소득기준=6572404, 3인가구 소득기준=9152368, 4인가구 소득기준=10293706, 5인가구 소득기준=10837258으로 답변해라.
            
            자동차 소유 조건이 존재한다면 자동차가액 기준을 나열하고, 존재하지 않는다면 null. 자동차 무소유가 조건이라면 isCarPriceLimited=true, carPriceLimit=0이다.  만약 자동차가액 조건이 '공공주택 입주자 자산기준'을 준용한다면, 자동차가액=38030000으로 답변해라.
            
            자산 조건이 존재한다면 청년 자산기준과 신혼부부 자산기준을 나열하고, 존재하지 않는다면 null. 만약 자산 조건이 '공공주택 입주자 자산기준'을 준용한다면, 청년 자산기준=254000000, 신혼부부 자산기준=337000000으로 답변해라. 청년과 신혼부부의 구분이 없다면 둘다 같은 값으로 나열해라.
            
            장애인 등과 같은 특별한 신청자격 보다 일반적인 신청자격을 바탕으로 답변해라. 답변의 예시는 다음과 같다.
            { "isIncomeLimited": true, "incomeLimit1Person": 3500000, "incomeLimit2Person": 6000000, "incomeLimit3Person": 8000000, "incomeLimit4Person": 10000000, "incomeLimit5Person": 12000000, "isCarPriceLimited": false, "carPriceLimit": null, "isAssetLimited": true, "youngManAssetLimit": 350000000, "newlyMarriedCoupleAssetLimit": 450000000 }
            """;

    public static final String POST_SCHEDULE_DEVELOPER_MSG = """
            Respond based on the content of the rental housing announcement PDF file provided by the user. Only answer in JSON format according to the user’s request; do not provide any other explanation or conversation. The keys in the JSON response must be written in English, but the values must be exactly as they appear in Korean in the PDF file.
            
            Answer in the order of the supply schedule, numbering sequentially starting from 1.
            
            If a schedule is given as a point in time rather than a period, treat it as “start,” not “end.”
            
            Dates must follow the format “yyyy-MM-dd” and date-times must follow “yyyy-MM-dd’T’HH:mm:ss”. If the time is unknown, set the start date-time to 00:00:00 and the end date-time to 23:59:59. If the schedule is not in the form of a date or date-time, but is written as a string, set “date” and “datetime” as null, and write the provided string in “note”.
            
            startDate and startDateTime represent the same concept, so both must have a value or both must be null.
            
            Change the following Korean values to the specified English values:
            - "모집공고": "NOTICE"
            - "청약신청 접수": "APPLICATION"
            - "서류심사 대상자 발표": "DOC_ANNOUNCEMENT"
            - "서류제출": "DOC_SUBMISSION"
            - "계약체결": "CONTRACT"
            - "입주기간": "MOVE_IN"
            - "기타", "분류불가": "UNCLASSIFIED"
            
            Change the following Korean keys to the specified English keys:
            - "순서": "ordering"
            - "일정유형": "scheduleType"
            - "일정명": "title"
            - "시작일자": "startDate"
            - "시작일시": "startDateTime"
            - "시작비고": "startNote"
            - "종료일시": "endDate"
            - "종료비고": "endNote"
            
            Values such as deposit and rent amounts must be answered as numbers, not as strings.
            """;

    public static final String POST_SCHEDULE_USER_MSG = """
            해당 공고문에서 공급 일정을 추출해라. 공급일정명을 바탕으로 ("NOTICE", "APPLICATION",  "DOC_ANNOUNCEMENT",  "DOC_SUBMISSION",  "CONTRACT",  "MOVE_IN")중 적절한 일정유형(scheduleType)으로 분류해라. 만약 제시된 유형 중 일치하는 유형이 없다면 반드시 "UNCLASSIFIED"으로 분류해줘.
            
            답변의 예시는 다음과 같다.
            [   {     "ordering": 1,     "scheduleType": "NOTICE",     "title": "임차인 모집공고",     "startDate": "2023-10-01",     "startDateTime": "2023-10-01T00:00:00",     "startNote": null,     "endDateTime": null,     "endNote": null   },   {     "ordering": 2,     "scheduleType": "APPLICATION",     "title": "청약신청 접수",     "startDate": "2023-10-22",     "startDateTime": "2023-10-22T00:00:00",     "startNote": null,     "endDateTime": "2023-10-28T00:00:00",     "endNote": null   },   {     "ordering": 3,     "scheduleType": "DOC_ANNOUNCEMENT",     "title": "서류심사 대상자 발표",     "startDate": "2023-10-26",     "startDateTime": "2023-10-26T15:00:00",     "startNote": null,     "endDateTime": null,     "endNote": null   },   {     "ordering": 4,     "scheduleType": "DOC_SUBMISSION",     "title": "서류제출",     "startDate": null,     "startDateTime": null,     "startNote": "추가 공고가 업로드된 후",     "endDateTime": null,     "endNote": null   } ]
            """;
}
