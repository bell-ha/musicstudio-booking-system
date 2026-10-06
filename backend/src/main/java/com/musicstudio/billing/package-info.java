/**
 * 수납 관리: 청구서, 입금·환불 장부, 영수증 (FR-PAY-01~05). 기관 모듈 BILLING(학원 관리 필요).
 * billing → academy → organization 방향만. academy는 이 모듈을 모르고, 수강 등록 이벤트만 낸다.
 * 돈은 학원 밖에서 오가고 앱은 기록한다(PG 없음).
 */
package com.musicstudio.billing;
