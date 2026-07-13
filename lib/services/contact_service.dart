import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/emergency_contact.dart';

class ContactService {
  static const String _storageKey = "emergency_contacts";
  static const int maxContacts = 5;

  /// Load all contacts
  Future<List<EmergencyContact>> getContacts() async {
    final prefs = await SharedPreferences.getInstance();

    final List<String> contactList =
        prefs.getStringList(_storageKey) ?? [];

    return contactList
        .map(
          (contact) => EmergencyContact.fromJson(
        jsonDecode(contact),
      ),
    )
        .toList();
  }

  /// Save complete list
  Future<void> saveContacts(
      List<EmergencyContact> contacts) async {
    final prefs = await SharedPreferences.getInstance();

    final List<String> encodedContacts =
    contacts.map((contact) {
      return jsonEncode(contact.toJson());
    }).toList();

    await prefs.setStringList(
      _storageKey,
      encodedContacts,
    );
  }

  /// Add one contact
  Future<bool> addContact(
      EmergencyContact contact) async {
    final contacts = await getContacts();

    if (contacts.length >= maxContacts) {
      return false;
    }

    contacts.add(contact);

    await saveContacts(contacts);

    return true;
  }

  /// Delete contact
  Future<void> deleteContact(int index) async {
    final contacts = await getContacts();

    contacts.removeAt(index);

    await saveContacts(contacts);
  }

  /// Update contact
  Future<void> updateContact(
      int index,
      EmergencyContact contact) async {
    final contacts = await getContacts();

    contacts[index] = contact;

    await saveContacts(contacts);
  }
}
