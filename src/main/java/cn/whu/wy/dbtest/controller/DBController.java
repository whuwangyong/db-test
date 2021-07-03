package cn.whu.wy.dbtest.controller;

import cn.whu.wy.dbtest.service.DBService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class DBController {

    @Autowired
    private DBService dbService;

    @GetMapping("/insert")
    public void insert(@RequestParam int num) {
        dbService.init();
        dbService.insert(num);
    }

    @GetMapping("/update")
    public void update() {

    }

    @GetMapping("/batchInsert")
    public void batchInsert(@RequestParam int num, @RequestParam int batchSize) {
        dbService.init();
        dbService.batchInsert(num, batchSize);
    }

    @GetMapping("/batchInsert2")
    public Object batchInsert2(@RequestParam int num, @RequestParam int batchSize) {
        dbService.init();
        return dbService.batchInsert2(num, batchSize);
    }

    @GetMapping("/batchInsert3")
    public void batchInsert3(@RequestParam int num, @RequestParam int batchSize) {
        dbService.init();
        dbService.batchInsert3(num, batchSize);
    }


}
