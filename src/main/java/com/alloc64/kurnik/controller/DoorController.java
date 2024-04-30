package com.alloc64.kurnik.controller;

import com.alloc64.kurnik.model.ChangeTimesRequest;
import com.alloc64.kurnik.model.DoorState;
import com.alloc64.kurnik.model.State;
import com.alloc64.kurnik.service.DoorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class DoorController {
    private final DoorService doorService;

    @GetMapping("/")
    public String index(Model model) throws Exception {
        model.addAttribute("state", doorService.getCurrentState());
        return "index";
    }

    @PostMapping("/change-state/{doorId}/{changeToState}")
    public String changeState(@PathVariable Integer doorId,
                              @PathVariable String changeToState,
                              Model model) {

        try {
            doorService.changeState(doorId, changeToState);
            //state.setMessage("Changed state to of doors " + doorId + " to "  + changeToState);
            return "redirect:/";
        }
        catch (Exception e) {
            State state = new State();
            model.addAttribute("state", state);
            state.setMessage("Failed to change state of doors " + doorId + " to " + changeToState + ": " + e.getMessage());

            return "index";
        }
    }

    @PostMapping("/change-times/{doorId}")
    public String changeTimes(@PathVariable Integer doorId,
                              @ModelAttribute ChangeTimesRequest request,
                              Model model) {

        try {
            doorService.changeTimes(doorId, request);
            return "redirect:/";
        }
        catch (Exception e) {
            State state = new State();
            state.setMessage("Failed to change times of door " + doorId + ": " + e.getMessage());
            model.addAttribute("state", state);

            return "index";
        }
    }
}
