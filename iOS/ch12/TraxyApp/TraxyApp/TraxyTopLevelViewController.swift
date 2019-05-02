//
//  TraxyTopLevelViewController.swift
//  TraxyApp
//
//  Created by Jonathan Engelsma on 10/17/17.
//  Copyright © 2017 Jonathan Engelsma. All rights reserved.
//

import UIKit
import Firebase
import FirebaseDatabase

class TraxyTopLevelViewController: UIViewController {
    var shouldLoad = true
    var userEmail : String?
    var journals : [Journal]? {
        didSet {
            self.journalsDidLoad()
        }
    }
    var ref : DatabaseReference?
    var userId : String? = "" {
        didSet {
            if userId != nil && userId != "" {
                // pop off any controllers beyond this one.
                if var count = self.navigationController?.children.count
                {
                    if count > 1 {
                        count -= 1
                        for _ in 1...count {
                            _ = self.navigationController?.popViewController(
                                animated: true)
                        }
                    }
                }
                self.ref = Database.database().reference()
                if self.shouldLoad {
                    self.registerForFireBaseUpdates()
                }
            }
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
    }
    
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if let tbc = self.tabBarController as? TraxyTabBarViewController {
            self.userId = tbc.userId
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // unregister from listeners here.
        if let r = self.ref {
            r.removeAllObservers()
        }
    }
    
    fileprivate func registerForFireBaseUpdates()
    {
        
        self.ref!.child(self.userId!).observe(.value, with: { [weak self] snapshot in
            guard let strongSelf = self else { return }
            
            if let postDict = snapshot.value as? [String : AnyObject] {
                var tmpItems = [Journal]()
                for (_,val) in postDict.enumerated() {
                    let entry = val.1 as! Dictionary<String,AnyObject>
                    let key = val.0
                    let name : String? = entry["name"] as! String?
                    let location : String?  = entry["address"] as! String?
                    let startDateStr  = entry["startDate"] as! String?
                    let endDateStr = entry["endDate"] as! String?
                    let lat = entry["lat"] as! Double?
                    let lng = entry["lng"] as! Double?
                    let placeId = entry["placeId"] as! String?
                    var coverPhotoUrl = entry["coverPhotoUrl"] as! String?
                    
                    var entries : [String : AnyObject]? = nil
                    if let e = entry["entries"] as? [String : AnyObject] {
                        entries = e
                        // if no photo is marked as cover, we will use first photo, if any.
                        if coverPhotoUrl == nil || coverPhotoUrl == "" {
                            for (_,val) in e.enumerated() {
                                let entry = val.1 as! Dictionary<String,AnyObject>
                                let typeRaw = entry["type"] as! Int?
                                let type = EntryType(rawValue: typeRaw!)
                                if type == .photo {
                                    let url : String? = entry["url"] as! String?
                                    coverPhotoUrl = url
                                    break
                                }
                            }
                        }
                    }
                    tmpItems.append(Journal(key: key, name: name, location: location, 
                                            startDate: startDateStr?.dateFromISO8601, endDate: 
                        endDateStr?.dateFromISO8601, lat: lat, lng: lng, placeId: placeId, 
                                                     coverPhotoUrl: coverPhotoUrl, entries: entries))
                }
                strongSelf.journals = tmpItems
            }
        })
    }	
    
    @IBAction func logout() {
        // Note we need not explicitly do a segue as the auth listener on our
        // top level tab bar controller will detect and put up the login.
        do {
            try Auth.auth().signOut()
            print("Logged out")
        } catch let signOutError as NSError {
            print ("Error signing out: %@", signOutError)
        }
        self.journals?.removeAll()
        self.journals = nil
        self.userEmail = nil
    }
    
    // Hook that gets called after journals are loaded.
    func journalsDidLoad()
    {
    }
}
