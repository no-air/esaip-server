package com.noair.easip.batch.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noair.easip.house.domain.House;
import com.noair.easip.house.service.HouseService;
import com.noair.easip.post.controller.dto.PostFlatDto;
import com.noair.easip.post.controller.dto.PostHouseFlatDto;
import com.noair.easip.post.controller.dto.PostScheduleFlatDto;
import com.noair.easip.post.domain.Post;
import com.noair.easip.post.domain.PostHouse;
import com.noair.easip.post.domain.PostHouseId;
import com.noair.easip.post.domain.PostSchedule;
import com.noair.easip.post.service.PostHouseService;
import com.noair.easip.post.service.PostScheduleService;
import com.noair.easip.post.service.PostService;
import com.noair.easip.util.FileUtils;
import com.noair.easip.util.KoreanStringConvertor;
import com.noair.easip.web.component.GptGateway;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.noair.easip.util.IdGenerator.generateUlid;
import static org.jsoup.Connection.Method.GET;
import static org.jsoup.Connection.Response;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlService {
    private static final String BASE_URL = "https://soco.seoul.go.kr";
    private static final String POST_DETAIL_URL = "https://soco.seoul.go.kr/youth/bbs/BMSR00015/view.do?menuNo=400008&boardId=";
    private static final String POST_LIST_API_URL = "https://soco.seoul.go.kr/youth/pgm/home/yohome/bbsListJson.json";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GptGateway gptGateway;

    private final PostCrawlingHistoryRepository postCrawlingHistoryRepository;
    private final CrawledPostHistoryRepository crawledPostHistoryRepository;
    private final HouseService houseService;
    private final PostService postService;
    private final PostScheduleService postScheduleService;
    private final PostHouseService postHouseService;

    @Transactional
    public boolean crawlPostList() {
        LocalDateTime now = LocalDateTime.now();
        boolean hasNextPost = true;
        PostCrawlingHistory postCrawlingHistory = PostCrawlingHistory.builder()
                .crawlingDateTime(LocalDateTime.now())
                .build();

        // TODO: debug용 page 범위 풀고 배포하기
        int page = 6;
        while (hasNextPost && page < 8) { // 무한루프 방지, 최대 1000페이지까지 크롤링
            // POST 파라미터 구성
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("bbsId", "BMSR00015");
            params.add("pageIndex", String.valueOf(page));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // POST 요청 및 JSON 파싱
            ResponseEntity<String> response = restTemplate.postForEntity(POST_LIST_API_URL, request, String.class);
            try {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode posts = rootNode.get("resultList");

                if (!posts.isArray()) {
                    throw new PostCrawlingParsingException();
                }

                // 마지막 페이지 도달
                if (posts.isEmpty()) {
                    break;
                }

                for (JsonNode post : posts) {
                    postCrawlingHistory.incrementReadPostCnt();
                    String boardId = post.get("boardId").asText();
                    String title = post.get("nttSj").asText();
                    String type = post.get("optn2").asText(); // 1:공공임대, 2:민간임대

                    // 크로링 데이터 이력에 boardId가 동일한 이력이 있을 경우
                    if (crawledPostHistoryRepository.existsByBoardId(boardId)) {
                        CrawledPostHistory oldHistory = crawledPostHistoryRepository.findByBoardId(boardId);

                        if (title.contains(oldHistory.getTitle()) && title.contains("수정")) {
                            // 제목이 과거 이력의 제목을 포함하고 "수정"이라고 기재된 경우, "공고 수정 진행"
                            log.warn("Modified post detected. Proceeding with update: {} - {} ==================================================================", boardId, title);
                            updatePost(boardId, now);
                            postCrawlingHistory.incrementUpdatePostCnt();

                        } else if (title.equals(oldHistory.getTitle())) {
                            // 제목이 과거 이력의 제목과 동일한 경우 신규 데이터가 없으므로, "크롤링 중단"
                            log.info("No new post detected. Stopping crawling for boardId: {} - {} ==================================================================", boardId, title);
                            hasNextPost = false;
//                            break;

                        } else {
                            // 그 외의 경우에는 정합성 문제 발생이므로, "실패 처리"
                            log.error("Duplicate boardId detected with different title. This is an inconsistency issue: {} - {} ==================================================================", boardId, title);
                            throw new PostCrawlingDuplicatedBoardIdException();
                        }

                    } else {
                        // 크로링 데이터 이력에 boardId가 동일한 이력이 없는 경우, "신규 공고 등록 진행"
                        if (type.contains("1")) {
                            log.warn("공공임대 공고가 업로드되었습니다. 수동 확인이 필요합니다.");
                            log.warn("Public rental housing post has been uploaded. Manual verification is required: {} - {}", boardId, title);
                            continue;
                        } else {
                            log.info("New post detected. Proceeding with insertion: {} - {} ==================================================================", boardId, title);
                            insertNewPost(boardId, now);
                            postCrawlingHistory.incrementAddPostCnt();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new PostCrawlingParsingException();
            }

            page++;
        }

        savePostCrawlingHistory(postCrawlingHistory);
        return postCrawlingHistory.getIsSuccess();
    }

    public CrawlPostDetailDto crawlPostDetail(String boardId) {
        try {
            String url = POST_DETAIL_URL + boardId;
            Response response = Jsoup.connect(url)
                    .method(GET)
                    .execute();
            Document doc = response.parse();

            // 제목
            String title = doc.select("div.view_info p.subject").text();
            // 주택 주소
            String address = doc.select("div.board_cont p").stream()
                    .map(row -> KoreanStringConvertor.extractAddress(row.text()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(PostCrawlingParsingException::new);

            // 주택 매칭키 추출
            String compactAddress = KoreanStringConvertor.toCompactAddress(address);
            String compactHouseName = doc.select("div.board_cont p").stream()
                    .map(row -> KoreanStringConvertor.extractHouseName(row.text()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            String compactHouseUrl = doc.select("div.board_cont p").stream()
                    .map(row -> KoreanStringConvertor.extractPageUrl(row.text()))
                    .filter(Objects::nonNull)
                    .map(KoreanStringConvertor::toCompactUrl)
                    .findFirst()
                    .orElse(null);

            Element postFile = doc.select("div.view_info ul li span.file span a").first();
            if (postFile == null) {
                log.warn("Post file link not found for boardId: {}", boardId);
                return null;
            }

            String postFileUrl = BASE_URL + postFile.attr("href");
            String postFileBase64 = FileUtils.downloadAndConvertToBase64(postFileUrl);
            String postFileName = doc.select("div.view_info ul li span.file span a").first().text();

            return CrawlPostDetailDto.of(
                    boardId,
                    title,
                    address,
                    compactAddress,
                    compactHouseName,
                    compactHouseUrl,
                    postFileBase64,
                    postFileName
            );
        } catch (IOException e) {
            e.printStackTrace();
            throw new PostCrawlingParsingException();
        } catch (Exception e) {
            e.printStackTrace();
            throw new PostCrawlingUnhandledException();
        }
    }

    @Transactional
    public void updatePost(String boardId, LocalDateTime crawlingDateTime) {
        // TODO: 게시글 수정 로직 보류. 추후 구현 필요
    }

    @Transactional
    public void insertNewPost(String boardId, LocalDateTime crawlingDateTime) {
        CrawlPostDetailDto crawlPostDetailDto = crawlPostDetail(boardId);
        if (crawlPostDetailDto == null) { // 크롤러의 문제가 아니라 공고에 충분한 데이터가 없는 경우, skip
            return;
        }

        log.info("crawlPostDetailDto: {}", crawlPostDetailDto.title());

        log.info("Crawled post matching key - compactAddress: {}, compactHouseName: {}, compactPageUrl: {}",
                crawlPostDetailDto.compactAddress(),
                crawlPostDetailDto.compactHouseName(),
                crawlPostDetailDto.compactPageUrl()
        );
        House house = houseService.getHouseByCompact(crawlPostDetailDto.compactAddress(), crawlPostDetailDto.compactHouseName(), crawlPostDetailDto.compactPageUrl());
        log.info("Matched house found - houseId: {}", house.getId());

        PostFlatDto postFlatDto = gptGateway.askPost(crawlPostDetailDto.postFileName(), crawlPostDetailDto.postFileBase64());
        log.info("postFlatDto:  {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}",
                postFlatDto.isIncomeLimited(),
                postFlatDto.incomeLimit1Person(),
                postFlatDto.incomeLimit2Person(),
                postFlatDto.incomeLimit3Person(),
                postFlatDto.incomeLimit4Person(),
                postFlatDto.incomeLimit5Person(),
                postFlatDto.isCarPriceLimited(),
                postFlatDto.carPriceLimit(),
                postFlatDto.isAssetLimited(),
                postFlatDto.youngManAssetLimit(),
                postFlatDto.newlyMarriedCoupleAssetLimit()
        );
        Post post = postService.create(
                Post.builder()
                        .id(generateUlid())
                        .title(crawlPostDetailDto.title())
                        .isIncomeLimited(postFlatDto.isIncomeLimited())
                        .incomeLimit1Person(Optional.ofNullable(postFlatDto.incomeLimit1Person()).map(Number::doubleValue).orElse(null))
                        .incomeLimit2Person(Optional.ofNullable(postFlatDto.incomeLimit2Person()).map(Number::doubleValue).orElse(null))
                        .incomeLimit3Person(Optional.ofNullable(postFlatDto.incomeLimit3Person()).map(Number::doubleValue).orElse(null))
                        .incomeLimit4Person(Optional.ofNullable(postFlatDto.incomeLimit4Person()).map(Number::doubleValue).orElse(null))
                        .incomeLimit5Person(Optional.ofNullable(postFlatDto.incomeLimit5Person()).map(Number::doubleValue).orElse(null))
                        .isCarPriceLimited(postFlatDto.isCarPriceLimited())
                        .carPriceLimit(Optional.ofNullable(postFlatDto.carPriceLimit()).map(Number::doubleValue).orElse(null))
                        .isAssetLimited(postFlatDto.isAssetLimited())
                        .youngManAssetLimit(Optional.ofNullable(postFlatDto.youngManAssetLimit()).map(Number::doubleValue).orElse(null))
                        .newlyMarriedCoupleAssetLimit(Optional.ofNullable(postFlatDto.newlyMarriedCoupleAssetLimit()).map(Number::doubleValue).orElse(null))
                        .build()
        );

        List<PostScheduleFlatDto> postScheduleFlatDtos = gptGateway.askPostSchedules(crawlPostDetailDto.postFileName(), crawlPostDetailDto.postFileBase64());
        postScheduleFlatDtos.forEach(postScheduleFlatDto -> log.info("postScheduleFlatDto: {} - {}, {}, {}, {}, {}, {}, {}",
                postScheduleFlatDto.ordering(),
                postScheduleFlatDto.title(),
                postScheduleFlatDto.scheduleType(),
                postScheduleFlatDto.startDate(),
                postScheduleFlatDto.startDateTime(),
                postScheduleFlatDto.startNote(),
                postScheduleFlatDto.endDateTime(),
                postScheduleFlatDto.endNote()
        ));
        postScheduleFlatDtos.forEach(dto ->
                postScheduleService.create(
                        PostSchedule.builder()
                                .id(generateUlid())
                                .ordering(dto.ordering())
                                .title(dto.title())
                                .scheduleType(dto.scheduleType())
                                .startDate(dto.startDate())
                                .startDateTime(dto.startDateTime())
                                .startNote(dto.startNote())
                                .endDateTime(dto.endDateTime())
                                .endNote(dto.endNote())
                                .post(post)
                                .build()
                )
        );

        List<PostHouseFlatDto> postHouseFlatDtos = gptGateway.askPostHouses(crawlPostDetailDto.postFileName(), crawlPostDetailDto.postFileBase64());
        postHouseFlatDtos.forEach(postHouseFlatDto ->
                log.info("PostHouseHouseFlatDto: {}, {}, {}, {}, {}",
                        postHouseFlatDto.supplyType(),
                        postHouseFlatDto.livingType(),
                        postHouseFlatDto.deposit(),
                        postHouseFlatDto.monthlyRent(),
                        postHouseFlatDto.supplyRoomCount()
                )
        );
        postHouseFlatDtos.forEach(dto ->
                postHouseService.create(
                        PostHouse.builder()
                                .id(
                                        PostHouseId.builder()
                                                .houseId(house.getId())
                                                .supplyType(dto.supplyType())
                                                .livingType(dto.livingType())
                                                .deposit(dto.deposit())
                                                .monthlyRent(dto.monthlyRent())
                                                .build()
                                )
                                .supplyRoomCount(dto.supplyRoomCount())
                                .house(house)
                                .post(post)
                                .build()
                )
        );


        saveCrawledPostHistory(
                CrawledPostHistory.builder()
                        .boardId(crawlPostDetailDto.boardId())
                        .title(crawlPostDetailDto.title())
                        .postFileName(crawlPostDetailDto.postFileName())
                        .insertPostId(post.getId()) // 시스템 ID로 등록
                        .updateHouseId(house.getId())
                        .crawlingDateTime(crawlingDateTime)
                        .build()
        );
    }

    public void savePostCrawlingHistory(PostCrawlingHistory postCrawlingHistory) {
        postCrawlingHistoryRepository.save(postCrawlingHistory);
    }

    public void saveCrawledPostHistory(CrawledPostHistory crawledPostHistory) {
        crawledPostHistoryRepository.save(crawledPostHistory);
    }
}
