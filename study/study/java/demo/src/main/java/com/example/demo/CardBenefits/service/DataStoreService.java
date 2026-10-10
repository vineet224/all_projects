package com.example.demo.CardBenefits.service;

import com.example.demo.CardBenefits.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.InitializingBean;
import java.util.*;

@Service
public class DataStoreService implements InitializingBean {
    private List<Airport> airportList;
    private List<Reimbursement> reimbursements;

    private Map<String, Airport> airportByCode;
    private Map<String, Terminal> terminalById;
    private Map<String, Outlet> outletById;
    private Map<String, List<Reimbursement>> reimbursementsByUser;

    @Override
    public void afterPropertiesSet() throws Exception {
        airportList = new ArrayList<>();
        reimbursements = new ArrayList<>();

        airportByCode = new HashMap<>();
        terminalById = new HashMap<>();
        outletById = new HashMap<>();
        reimbursementsByUser = new HashMap<>();
        
        createHardcodedData();
    }
    
    private void createHardcodedData() {
        Airport delhiAirport = new Airport();
        delhiAirport.setId("DEL001");
        delhiAirport.setAirportCode("DEL");
        delhiAirport.setName("Indira Gandhi International Airport");
        delhiAirport.setCity("Delhi");
        delhiAirport.setState("Delhi");
        delhiAirport.setTerminals(new ArrayList<>());
        
        Airport mumbaiAirport = new Airport();
        mumbaiAirport.setId("BOM001");
        mumbaiAirport.setAirportCode("BOM");
        mumbaiAirport.setName("Chhatrapati Shivaji Maharaj International Airport");
        mumbaiAirport.setCity("Mumbai");
        mumbaiAirport.setState("Maharashtra");
        mumbaiAirport.setTerminals(new ArrayList<>());
        
        Terminal delhiT1 = new Terminal();
        delhiT1.setId("DEL_T1");
        delhiT1.setName("T1");
        delhiT1.setAirportId("DEL001");
        delhiT1.setOutlets(new ArrayList<>());
        
        Terminal delhiT3 = new Terminal();
        delhiT3.setId("DEL_T3");
        delhiT3.setName("T3");
        delhiT3.setAirportId("DEL001");
        delhiT3.setOutlets(new ArrayList<>());
        
        Terminal mumbaiT1 = new Terminal();
        mumbaiT1.setId("BOM_T1");
        mumbaiT1.setName("T1");
        mumbaiT1.setAirportId("BOM001");
        mumbaiT1.setOutlets(new ArrayList<>());
        
        Outlet delhiT1Lounge = new Outlet();
        delhiT1Lounge.setId("DEL_T1_LOUNGE_1");
        delhiT1Lounge.setName("Premium Lounge T1");
        delhiT1Lounge.setType(OutletTypeEnum.LOUNGE);
        delhiT1Lounge.setTerminalId("DEL_T1");
        
        Outlet delhiT1Shop = new Outlet();
        delhiT1Shop.setId("DEL_T1_SHOP_1");
        delhiT1Shop.setName("Duty Free Shop T1");
        delhiT1Shop.setType(OutletTypeEnum.SHOPPING);
        delhiT1Shop.setTerminalId("DEL_T1");
        
        Outlet delhiT3Lounge = new Outlet();
        delhiT3Lounge.setId("DEL_T3_LOUNGE_1");
        delhiT3Lounge.setName("Premium Lounge T3");
        delhiT3Lounge.setType(OutletTypeEnum.LOUNGE);
        delhiT3Lounge.setTerminalId("DEL_T3");
        
        Outlet delhiT3Restaurant = new Outlet();
        delhiT3Restaurant.setId("DEL_T3_REST_1");
        delhiT3Restaurant.setName("Fine Dining T3");
        delhiT3Restaurant.setType(OutletTypeEnum.RESTAURANT);
        delhiT3Restaurant.setTerminalId("DEL_T3");
        
        Outlet mumbaiT1Lounge = new Outlet();
        mumbaiT1Lounge.setId("BOM_T1_LOUNGE_1");
        mumbaiT1Lounge.setName("Premium Lounge BOM T1");
        mumbaiT1Lounge.setType(OutletTypeEnum.LOUNGE);
        mumbaiT1Lounge.setTerminalId("BOM_T1");
        
        Outlet mumbaiT1Shop = new Outlet();
        mumbaiT1Shop.setId("BOM_T1_SHOP_1");
        mumbaiT1Shop.setName("Duty Free Shop BOM T1");
        mumbaiT1Shop.setType(OutletTypeEnum.SHOPPING);
        mumbaiT1Shop.setTerminalId("BOM_T1");
        
        delhiT1.getOutlets().addAll(Arrays.asList(delhiT1Lounge, delhiT1Shop));
        delhiT3.getOutlets().addAll(Arrays.asList(delhiT3Lounge, delhiT3Restaurant));
        mumbaiT1.getOutlets().addAll(Arrays.asList(mumbaiT1Lounge, mumbaiT1Shop));
        
        delhiAirport.getTerminals().addAll(Arrays.asList(delhiT1, delhiT3));
        mumbaiAirport.getTerminals().addAll(Arrays.asList(mumbaiT1));
        
        airportList.addAll(Arrays.asList(delhiAirport, mumbaiAirport));

        airportByCode.put("DEL", delhiAirport);
        airportByCode.put("BOM", mumbaiAirport);
        
        terminalById.put("DEL_T1", delhiT1);
        terminalById.put("DEL_T3", delhiT3);
        terminalById.put("BOM_T1", mumbaiT1);
        
        outletById.put("DEL_T1_LOUNGE_1", delhiT1Lounge);
        outletById.put("DEL_T1_SHOP_1", delhiT1Shop);
        outletById.put("DEL_T3_LOUNGE_1", delhiT3Lounge);
        outletById.put("DEL_T3_REST_1", delhiT3Restaurant);
        outletById.put("BOM_T1_LOUNGE_1", mumbaiT1Lounge);
        outletById.put("BOM_T1_SHOP_1", mumbaiT1Shop);
    }
    
    public Airport getAirportByCode(String airportCode) {
        return airportByCode.get(airportCode);
    }
    
    public Terminal getTerminalById(String terminalId) {
        return terminalById.get(terminalId);
    }
    
    public Outlet getOutletById(String outletId) {
        return outletById.get(outletId);
    }
    
    public List<Reimbursement> getReimbursementsByUser(String userId) {
        return reimbursementsByUser.getOrDefault(userId, new ArrayList<>());
    }
    
    public void addReimbursement(Reimbursement reimbursement) {
        reimbursements.add(reimbursement);
        reimbursementsByUser.computeIfAbsent(reimbursement.getUserId(), k -> new ArrayList<>()).add(reimbursement);
    }
    
    public List<Reimbursement> getAllReimbursements() {
        return new ArrayList<>(reimbursements);
    }
    
    public List<Airport> getAllAirports() {
        return new ArrayList<>(airportList);
    }
}
